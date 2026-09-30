package com.example.projectmm.application.world;

import com.example.projectmm.api.world.WorldCreateRequest;
import com.example.projectmm.api.world.WorldLoreResponse;
import com.example.projectmm.api.world.WorldResponse;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldLoreCategory;
import com.example.projectmm.domain.world.WorldLoreEntry;
import com.example.projectmm.domain.world.WorldLoreEntryRepository;
import com.example.projectmm.domain.world.WorldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldService {

    private final WorldRepository worldRepository;
    private final WorldLoreEntryRepository worldLoreEntryRepository;
    private final MemberRepository memberRepository;
    private final WorldLorePolicy worldLorePolicy;

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

    @Transactional(readOnly = true)
    public WorldResponse getById(Long worldId, String loginId) {
        return toResponse(findOwnedWorld(worldId, loginId));
    }

    @Transactional(readOnly = true)
    public WorldLoreResponse getLore(Long worldId, WorldLoreCategory category, String loginId) {
        World world = findOwnedWorld(worldId, loginId);
        List<String> entries = worldLoreEntryRepository
                .findByWorldIdAndCategoryOrderBySortOrderAsc(world.getId(), category)
                .stream()
                .map(WorldLoreEntry::getContent)
                .toList();
        return toLoreResponse(category, entries);
    }

    @Transactional
    public WorldLoreResponse replaceLore(
            Long worldId,
            WorldLoreCategory category,
            List<String> entries,
            String loginId
    ) {
        World world = findOwnedWorld(worldId, loginId);
        List<String> normalized = worldLorePolicy.normalizeAndValidate(category, entries);

        worldLoreEntryRepository.deleteByWorldIdAndCategory(world.getId(), category);
        for (int i = 0; i < normalized.size(); i++) {
            worldLoreEntryRepository.save(
                    new WorldLoreEntry(world, category, normalized.get(i), i)
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
        return new WorldResponse(world.getId(), world.getName(), world.getDescription());
    }

    private WorldLoreResponse toLoreResponse(WorldLoreCategory category, List<String> entries) {
        int totalCharacters = entries.stream().mapToInt(String::length).sum();
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
