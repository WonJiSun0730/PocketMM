package com.example.projectmm.application.world;

import com.example.projectmm.api.world.WorldCharacterItem;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldCharacterService {
    private final WorldRepository worlds;
    private final CharacterRepository characters;
    private final CharacterProfileRepository profiles;
    private final MemberRepository members;

    @Transactional(readOnly = true)
    public List<WorldCharacterItem> get(Long worldId, String loginId) {
        ownedWorld(worldId, loginId);
        return characters.findAllByWorldIdOrderByIdAsc(worldId).stream().map(this::item).toList();
    }

    @Transactional
    public List<WorldCharacterItem> replace(Long worldId, List<Long> characterIds, String loginId) {
        World world = ownedWorld(worldId, loginId);
        Long ownerId = world.getOwner().getId();
        List<Long> requested = characterIds == null ? List.of() : characterIds.stream().distinct().toList();

        for (Character character : characters.findAllByWorldIdOrderByIdAsc(worldId)) {
            if (!requested.contains(character.getId())) character.assignWorld(null);
        }
        for (Long characterId : requested) {
            Character character = characters.findByIdAndOwnerId(characterId, ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Character not found or not owned. characterId=" + characterId));
            character.assignWorld(world);
        }
        return characters.findAllByWorldIdOrderByIdAsc(worldId).stream().map(this::item).toList();
    }

    private WorldCharacterItem item(Character character) {
        String description = profiles.findByCharacterId(character.getId()).map(profile -> profile.getDescription()).orElse(null);
        return new WorldCharacterItem(character.getId(), character.getName(), description);
    }

    private World ownedWorld(Long worldId, String loginId) {
        Long ownerId = members.findByLoginId(loginId).orElseThrow(() -> new IllegalArgumentException("Member not found.")).getId();
        return worlds.findByIdAndOwnerId(worldId, ownerId).orElseThrow(() -> new IllegalArgumentException("World not found."));
    }
}
