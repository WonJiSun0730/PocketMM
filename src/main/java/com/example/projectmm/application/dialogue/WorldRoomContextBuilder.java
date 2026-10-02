package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.character.CharacterExample;
import com.example.projectmm.domain.character.CharacterExampleRepository;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldConversationCastRepository;
import com.example.projectmm.domain.chat.WorldConversationRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionCastRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionSystemStateRepository;
import com.example.projectmm.domain.chat.WorldRoomMessage;
import com.example.projectmm.domain.chat.WorldRoomMessageRepository;
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
    private final WorldRoomSessionSystemStateRepository sessionSystemStates;
    private final WorldRoomMessageRepository messages;
    private final CharacterProfileRepository profiles;
    private final CharacterStateRepository states;
    private final CharacterExampleRepository examples;
    private final WorldLoreEntryRepository loreEntries;
    private final WorldSystemRepository systems;
    private final WorldSystemStateRepository systemStates;
    private final MemoryRecallService memoryRecallService;

    @Transactional(readOnly = true)
    public WorldRoomDialogueContext buildSession(Long sessionId) {
        return buildSession(sessionId, null);
    }

    @Transactional(readOnly = true)
    public WorldRoomDialogueContext buildSessionBeforeMessage(Long sessionId, Long beforeMessageId) {
        if (beforeMessageId == null) {
            throw new IllegalArgumentException("beforeMessageId must not be null");
        }
        return buildSession(sessionId, beforeMessageId);
    }

    private WorldRoomDialogueContext buildSession(Long sessionId, Long beforeMessageId) {
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

        var recentMessages = (beforeMessageId == null
                ? messages.findTop50BySessionIdOrderByCreatedAtDescIdDesc(sessionId)
                : messages.findTop50BySessionIdAndIdLessThanOrderByIdDesc(sessionId, beforeMessageId))
                .stream()
                .sorted(java.util.Comparator.comparing(WorldRoomMessage::getCreatedAt)
                        .thenComparing(WorldRoomMessage::getId))
                .toList();

        String memoryQuery = recentMessages.stream()
                .filter(message -> message.getRole() == ChatRole.USER)
                .reduce((first, second) -> second)
                .map(WorldRoomMessage::getContent)
                .orElseGet(() -> recentMessages.isEmpty()
                        ? ""
                        : recentMessages.getLast().getContent());

        var recalledMemories = activeCast.stream()
                .map(character -> new WorldRoomDialogueContext.CharacterMemories(
                        character.characterId(),
                        character.characterName(),
                        memoryRecallService.recallForWorldRoomSession(
                                        character.characterId(),
                                        sessionId,
                                        session.getSimulatedAt(),
                                        5,
                                        memoryQuery
                                ).stream()
                                .map(memory -> new WorldRoomDialogueContext.RecalledMemory(
                                        memory.memoryId(),
                                        memory.summary(),
                                        memory.evidenceQuote(),
                                        memory.importance(),
                                        memory.effectiveStrength(),
                                        memory.rememberedAt()
                                ))
                                .toList()
                ))
                .filter(group -> !group.memories().isEmpty())
                .toList();

        return new WorldRoomDialogueContext(
                room.getId(),
                room.getTitle(),
                room.getVisibility(),
                session.getSimulatedAt(),
                new DialogueWorldContext(
                        world.getId(),
                        world.getName(),
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
                session.getIntro().getTitle(),
                session.getIntro().getContent(),
                recentMessages.stream()
                        .map(message -> new DialogueMessageContext(
                                message.getRole(),
                                message.getContent(),
                                message.getCreatedAt()
                        ))
                        .toList(),
                roomCast,
                activeCast,
                sessionSystemStates.findBySessionIdOrderBySystemSortOrderAscIdAsc(sessionId)
                        .stream()
                        .map(state -> new WorldRoomSystemContext(
                                state.getSystem().getId(),
                                state.getSystem().getName(),
                                state.getCurrentValue(),
                                state.getSystem().getMinValue(),
                                state.getSystem().getMaxValue(),
                                state.getSystem().getChangeCondition(),
                                systemStates.findBySystemIdOrderBySortOrderAsc(state.getSystem().getId())
                                        .stream()
                                        .map(label -> new DialogueWorldSystemState(
                                                label.getMinValue(),
                                                label.getMaxValue(),
                                                label.getLabel()
                                        ))
                                        .toList()
                        ))
                        .toList(),
                recalledMemories
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
                            false
                    );
                })
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
