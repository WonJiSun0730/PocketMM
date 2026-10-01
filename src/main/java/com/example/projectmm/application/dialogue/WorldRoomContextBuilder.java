package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.character.CharacterExample;
import com.example.projectmm.domain.character.CharacterExampleRepository;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.WorldConversationCastRepository;
import com.example.projectmm.domain.chat.WorldConversationRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionCastRepository;
import com.example.projectmm.domain.dialogue.DialogueCharacterProfile;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.world.WorldLoreEntryRepository;
import com.example.projectmm.domain.world.WorldSystemRepository;
import com.example.projectmm.domain.world.WorldSystemStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WorldRoomContextBuilder {

    private final WorldConversationRepository rooms;
    private final WorldConversationCastRepository casts;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomSessionCastRepository sessionCasts;
    private final CharacterProfileRepository profiles;
    private final CharacterStateRepository states;
    private final CharacterExampleRepository examples;
    private final WorldLoreEntryRepository loreEntries;
    private final WorldSystemRepository systems;
    private final WorldSystemStateRepository systemStates;

    @Transactional(readOnly = true)
    public WorldRoomDialogueContext buildSession(Long sessionId) {
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("World room session not found. sessionId=" + sessionId));
        var room = session.getRoom();
        var world = room.getWorld();

        var activeIds = sessionCasts.findBySessionIdAndActiveTrueOrderByEnteredAtAscIdAsc(sessionId)
                .stream()
                .map(state -> state.getCharacter().getId())
                .collect(java.util.stream.Collectors.toSet());

        var roomCast = buildCast(room.getId()).stream()
                .map(character -> new WorldRoomCharacterContext(
                        character.characterId(),
                        character.characterName(),
                        character.profile(),
                        character.energy(),
                        character.mood(),
                        activeIds.contains(character.characterId())
                ))
                .toList();
        var activeCast = roomCast.stream()
                .filter(WorldRoomCharacterContext::active)
                .toList();

        var lore = loreEntries.findByWorldIdOrderByCategoryAscSortOrderAsc(world.getId())
                .stream()
                .map(entry -> new DialogueWorldLore(
                        entry.getCategory().name(),
                        entry.getTitle(),
                        entry.getContent()
                ))
                .toList();

        var worldSystems = systems.findByWorldIdOrderBySortOrderAsc(world.getId())
                .stream()
                .map(system -> new DialogueWorldSystem(
                        system.getName(),
                        system.getChangeCondition(),
                        system.getMinValue(),
                        system.getMaxValue(),
                        systemStates.findBySystemIdOrderBySortOrderAsc(system.getId())
                                .stream()
                                .map(state -> new DialogueWorldSystemState(
                                        state.getMinValue(),
                                        state.getMaxValue(),
                                        state.getLabel()
                                ))
                                .toList()
                ))
                .toList();

        return new WorldRoomDialogueContext(
                room.getId(),
                room.getTitle(),
                room.getVisibility(),
                session.getSimulatedAt(),
                new DialogueWorldContext(
                        world.getId(),
                        world.getName(),
                        world.getIntroduction(),
                        world.getPlayGuide(),
                        lore,
                        worldSystems
                ),
                session.getPlayerProfile() == null
                        ? null
                        : new DialoguePlayerProfile(
                                session.getPlayerProfile().getName(),
                                session.getPlayerProfile().getDescription()
                        ),
                roomCast,
                activeCast
        );
    }

    @Transactional(readOnly = true)
    public List<WorldRoomCharacterContext> buildCast(Long roomId) {
        rooms.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("World room not found. roomId=" + roomId));

        return casts.findByConversationIdOrderByJoinedAtAscIdAsc(roomId)
                .stream()
                .map(cast -> {
                    var character = cast.getCharacter();
                    var profile = profiles.findByCharacterId(character.getId()).orElse(null);
                    var state = states.findByCharacterId(character.getId()).orElse(null);
                    return new WorldRoomCharacterContext(
                            character.getId(),
                            character.getName(),
                            profile == null ? null : new DialogueCharacterProfile(
                                    profile.getDescription(),
                                    profile.getRawDescription(),
                                    buildExamples(character.getId(), profile.getExampleDialogue()),
                                    profile.getPersonality(),
                                    profile.getSpeechStyle(),
                                    profile.getBackground(),
                                    profile.getAppearance()
                            ),
                            state == null ? 70 : state.getEnergy(),
                            state == null ? 70 : state.getMood(),
                            cast.isActive()
                    );
                })
                .toList();
    }

    public List<WorldRoomCharacterContext> activeCast(Long roomId) {
        return buildCast(roomId).stream()
                .filter(WorldRoomCharacterContext::active)
                .toList();
    }

    private List<String> buildExamples(Long characterId, String legacyExampleDialogue) {
        List<String> stored = examples.findByCharacterIdOrderBySortOrderAsc(characterId)
                .stream()
                .map(CharacterExample::getContent)
                .toList();
        if (!stored.isEmpty()) {
            return stored;
        }
        if (legacyExampleDialogue == null || legacyExampleDialogue.isBlank()) {
            return List.of();
        }
        return List.of(legacyExampleDialogue.trim());
    }
}
