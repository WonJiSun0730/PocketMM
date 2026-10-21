package com.example.projectmm.application.world;

import com.example.projectmm.api.world.WorldCreateRequest;
import com.example.projectmm.api.world.WorldLoreItem;
import com.example.projectmm.api.world.WorldLoreResponse;
import com.example.projectmm.api.world.WorldResponse;
import com.example.projectmm.api.world.WorldUpdateRequest;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.WorldConversationRepository;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldLoreCategory;
import com.example.projectmm.domain.world.WorldLoreEntry;
import com.example.projectmm.domain.world.WorldLoreEntryRepository;
import com.example.projectmm.domain.world.WorldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldService {

    private final WorldRepository worldRepository;
    private final WorldLoreEntryRepository worldLoreEntryRepository;
    private final MemberRepository memberRepository;
    private final WorldLorePolicy worldLorePolicy;
    private final CharacterRepository characterRepository;
    private final WorldConversationRepository worldConversationRepository;
    private final com.example.projectmm.domain.world.WorldSystemRepository worldSystemRepository;
    private final com.example.projectmm.domain.world.WorldSystemStateRepository worldSystemStateRepository;

    @Transactional
    public WorldResponse create(WorldCreateRequest request, String loginId) {
        Member owner = findMember(loginId);
        World world = worldRepository.save(
                new World(owner, request.name().trim(), normalizeNullable(request.description()))
        );
        return toResponse(world);
    }

    @Transactional(readOnly = true)
    public List<WorldResponse> getAll(String loginId) {
        return worldRepository.findAllByOwnerIdOrderByIdDesc(findMember(loginId).getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WorldResponse update(Long worldId, WorldUpdateRequest request, String loginId) {
        World world = findOwnedWorld(worldId, loginId);
        world.updateMetadata(request.name().trim(), normalizeNullable(request.description()));
        return toResponse(world);
    }

    @Transactional
    public void delete(Long worldId, String loginId) {
        World world = findOwnedWorld(worldId, loginId);
        if (characterRepository.existsByWorldId(worldId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Characters are assigned to this world."
            );
        }
        if (worldConversationRepository.existsByWorldId(worldId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Rooms are assigned to this world."
            );
        }
        worldSystemStateRepository.deleteBySystemWorldId(worldId);
        worldSystemRepository.deleteByWorldId(worldId);
        worldLoreEntryRepository.deleteByWorldId(worldId);
        worldRepository.delete(world);
    }

    @Transactional(readOnly = true)
    public WorldResponse getById(Long worldId, String loginId) {
        return toResponse(findOwnedWorld(worldId, loginId));
    }

    @Transactional(readOnly = true)
    public WorldLoreResponse getLore(Long worldId, WorldLoreCategory category, String loginId) {
        World world = findOwnedWorld(worldId, loginId);
        List<WorldLoreItem> entries = worldLoreEntryRepository
                .findByWorldIdAndCategoryOrderBySortOrderAsc(world.getId(), category)
                .stream()
                .map(entry -> new WorldLoreItem(entry.getTitle(), entry.getContent()))
                .toList();
        return toLoreResponse(category, entries);
    }

    @Transactional
    public WorldLoreResponse replaceLore(
            Long worldId,
            WorldLoreCategory category,
            List<WorldLoreItem> entries,
            String loginId
    ) {
        World world = findOwnedWorld(worldId, loginId);
        List<WorldLoreItem> normalized = worldLorePolicy.normalizeAndValidate(category, entries);

        worldLoreEntryRepository.deleteByWorldIdAndCategory(world.getId(), category);
        for (int i = 0; i < normalized.size(); i++) {
            worldLoreEntryRepository.save(
                    new WorldLoreEntry(world, category, normalized.get(i).title(), normalized.get(i).content(), i)
            );
        }

        return toLoreResponse(category, normalized);
    }

    private World findOwnedWorld(Long worldId, String loginId) {
        Long ownerId = findMember(loginId).getId();
        return worldRepository.findByIdAndOwnerId(worldId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("World not found. worldId=" + worldId));
    }

    private Member findMember(String loginId) {
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found. loginId=" + loginId));
    }

    private WorldResponse toResponse(World world) {
        return new WorldResponse(world.getId(), world.getName(), world.getDescription(), world.getCoverImageUrl());
    }

    private WorldLoreResponse toLoreResponse(WorldLoreCategory category, List<WorldLoreItem> entries) {
        int totalCharacters = entries.stream()
                .mapToInt(entry -> (entry.title() == null ? 0 : entry.title().length()) + entry.content().length())
                .sum();
        return new WorldLoreResponse(
                category,
                entries,
                totalCharacters,
                category.getMaxTotalCharacters(),
                category.getMaxEntries()
        );
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
