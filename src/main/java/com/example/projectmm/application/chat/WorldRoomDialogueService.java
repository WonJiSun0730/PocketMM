package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.PocketUserSpeechCommitted;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldRoomMessage;
import com.example.projectmm.domain.chat.WorldRoomMessageRepository;
import com.example.projectmm.domain.chat.WorldRoomMessageVariant;
import com.example.projectmm.domain.chat.WorldRoomMessageVariantRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.domain.chat.WorldRoomPlayerState;
import com.example.projectmm.domain.chat.WorldRoomPlayerStateRepository;
import com.example.projectmm.domain.chat.WorldRoomVariantCastSnapshotRepository;
import com.example.projectmm.domain.chat.WorldRoomVariantSystemSnapshotRepository;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.ArrayList;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldRoomDialogueService {

    private final WorldRoomContextBuilder contextBuilder;
    private final DialogueGateway dialogueGateway;
    private final WorldRoomSessionService sessionService;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomPlayerStateRepository playerStates;
    private final ObjectMapper objectMapper;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final WorldRoomVariantCastSnapshotRepository castSnapshots;
    private final WorldRoomVariantSystemSnapshotRepository systemSnapshots;
    private final ApplicationEventPublisher events;
    private final MemoryRepository memories;
    private final ProtectedMemoryDetailRepository memoryDetails;
    private final TimeInterpreter timeInterpreter;

    @Transactional
    public WorldRoomTurn chat(Long sessionId, String userMessage) {
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));

        var userSourceMessage = messages.save(new WorldRoomMessage(
                session,
                ChatRole.USER,
                userMessage,
                now()
        ));

        var beforeTime = session.getSimulatedAt();
        boolean explicitTimeChanged = applyExplicitTimeChange(session, userMessage);
        var context = contextBuilder.buildSession(sessionId);
        var beforeCast = sessionService.captureCastState(sessionId);
        var beforeSystems = sessionService.captureSystemState(sessionId);
        var beforePlayerState = capturePlayerState(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, userMessage);
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        applySceneLocation(sessionId, generated);
        if (!explicitTimeChanged) {
            applyNarrativeElapsedTime(session, generated.elapsedSeconds());
        }
        var afterCast = sessionService.captureCastState(sessionId);
        var afterSystems = sessionService.captureSystemState(sessionId);

        var characterMessage = messages.save(new WorldRoomMessage(
                session,
                ChatRole.CHARACTER,
                generated.reply(),
                now()
        ));
        var variant = variants.save(new WorldRoomMessageVariant(
                characterMessage,
                1,
                generated.reply(),
                now()
        ));
        sessionService.saveCastSnapshot(variant, beforeCast, afterCast);
        sessionService.saveSystemSnapshot(variant, beforeSystems, afterSystems);
        sessionService.saveTimeSnapshot(variant, beforeTime, session.getSimulatedAt());
        savePlayerStateSnapshot(variant, beforePlayerState, sessionId);
        events.publishEvent(new PocketUserSpeechCommitted(
                sessionId,
                userSourceMessage.getId(),
                userMessage,
                context.simulatedAt()
        ));

        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    }

    private record PlayerStateSnapshot(String location, List<String> conditions, List<String> inventory) {}

    private String capturePlayerState(Long sessionId) {
        var state = playerStates.findBySessionId(sessionId).orElse(null);
        var snapshot = state == null
                ? new PlayerStateSnapshot(null, List.of(), List.of())
                : new PlayerStateSnapshot(state.getLocation(), List.copyOf(state.getConditions()), List.copyOf(state.getInventory()));
        return objectMapper.writeValueAsString(snapshot);
    }

    private void restorePlayerState(Long sessionId, String json) {
        if (json == null || json.isBlank()) return;
        try {
            var snapshot = objectMapper.readValue(json, PlayerStateSnapshot.class);
            var session = sessions.findById(sessionId).orElseThrow();
            var state = playerStates.findBySessionId(sessionId)
                    .orElseGet(() -> playerStates.save(new WorldRoomPlayerState(session)));
            state.update(snapshot.location(), snapshot.conditions(), snapshot.inventory());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore Pocket player-state snapshot", e);
        }
    }

    private void savePlayerStateSnapshot(WorldRoomMessageVariant variant, String before, Long sessionId) {
        variant.capturePlayerStateWorldline(before, capturePlayerState(sessionId));
    }

    private void applySceneLocation(Long sessionId, WorldRoomTurn generated) {
        String location = generated.locationChange();
        boolean hasLocation = location != null && !location.isBlank() && location.length() <= 200;
        boolean hasChanges = !generated.addedConditions().isEmpty()
                || !generated.removedConditions().isEmpty()
                || !generated.addedInventory().isEmpty()
                || !generated.removedInventory().isEmpty();
        if (!hasLocation && !hasChanges) return;
        var session = sessions.findById(sessionId).orElseThrow();
        var state = playerStates.findBySessionId(sessionId)
                .orElseGet(() -> playerStates.save(new WorldRoomPlayerState(session)));
        var conditions = new ArrayList<>(state.getConditions());
        var inventory = new ArrayList<>(state.getInventory());
        applyStateDelta(conditions, generated.removedConditions(), generated.addedConditions());
        applyStateDelta(inventory, generated.removedInventory(), generated.addedInventory());
        state.update(hasLocation ? location : state.getLocation(), conditions, inventory);
    }

    private void applyStateDelta(List<String> current, List<String> removed, List<String> added) {
        for (String item : removed) {
            if (item != null && !item.isBlank()) current.removeIf(existing -> existing.equalsIgnoreCase(item.trim()));
        }
        for (String item : added) {
            if (item != null && !item.isBlank() && item.length() <= 300
                    && current.size() < 50 && current.stream().noneMatch(existing -> existing.equalsIgnoreCase(item.trim()))) {
                current.add(item.trim());
            }
        }
    }

    private boolean applyExplicitTimeChange(
            com.example.projectmm.domain.chat.WorldRoomSession session,
            String userMessage
    ) {
        var interpretation = timeInterpreter.interpret(userMessage, session.getSimulatedAt());
        if (interpretation == null || !interpretation.matched() || interpretation.timeChange() == null) {
            return false;
        }

        var change = interpretation.timeChange();
        if (change.changed() && change.newTime() != null) {
            session.updateSimulatedAt(change.newTime());
            return true;
        }
        return false;
    }

    private void applyNarrativeElapsedTime(
            com.example.projectmm.domain.chat.WorldRoomSession session,
            long elapsedSeconds
    ) {
        if (elapsedSeconds <= 0) {
            return;
        }
        session.updateSimulatedAt(session.getSimulatedAt().plusSeconds(elapsedSeconds));
    }

    @Transactional
    public WorldRoomTurn continueScene(Long sessionId) {
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));

        var context = contextBuilder.buildSession(sessionId);
        var beforeCast = sessionService.captureCastState(sessionId);
        var beforeSystems = sessionService.captureSystemState(sessionId);
        var beforeTime = session.getSimulatedAt();
        var continuationPrompt = session.isNoControlPlayer()
                ? "[Continue the current scene naturally for one turn without controlling the player character. Advance only NPC actions, dialogue, environment, events, and other story elements. Do not invent any player dialogue, action, thought, feeling, reaction, decision, or movement. End with room for the user to respond.]"
                : "[Continue the current scene naturally for one turn. The player character may actively participate: give them plausible small actions, immediate reactions, observations, movement, or brief dialogue consistent with their PlayerProfile, established relationships, and the current situation. This mode is used when the user wants the story to move forward without deciding the next beat themselves. Keep major decisions, irreversible actions, major relationship changes, high-risk commitments, and other consequential choices open for the user.]";
        var beforePlayerState = capturePlayerState(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, continuationPrompt);
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        applySceneLocation(sessionId, generated);
        applyNarrativeElapsedTime(session, generated.elapsedSeconds());
        var afterCast = sessionService.captureCastState(sessionId);
        var afterSystems = sessionService.captureSystemState(sessionId);

        var characterMessage = messages.save(new WorldRoomMessage(
                session,
                ChatRole.CHARACTER,
                generated.reply(),
                now()
        ));
        var variant = variants.save(new WorldRoomMessageVariant(
                characterMessage,
                1,
                generated.reply(),
                now()
        ));
        sessionService.saveCastSnapshot(variant, beforeCast, afterCast);
        sessionService.saveSystemSnapshot(variant, beforeSystems, afterSystems);
        sessionService.saveTimeSnapshot(variant, beforeTime, session.getSimulatedAt());
        savePlayerStateSnapshot(variant, beforePlayerState, sessionId);

        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    }

    @Transactional
    public WorldRoomMessage editMessage(Long sessionId, Long messageId, String content) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        var message = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room message not found. messageId=" + messageId
                ));
        message.edit(content, now());
        if (message.getRole() == ChatRole.CHARACTER) {
            selectedVariant(message).editContent(content);
        }
        return message;
    }

    @Transactional
    public void deleteMessageAndFollowing(Long sessionId, Long messageId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room message not found. messageId=" + messageId
                ));
        messages.findTopBySessionIdAndIdGreaterThanEqualAndRoleOrderByIdAsc(
                        sessionId,
                        messageId,
                        ChatRole.CHARACTER
                )
                .ifPresent(characterMessage -> {
                    var variant = selectedVariant(characterMessage);
                    sessionService.restoreCastBeforeVariant(sessionId, variant);
                    sessionService.restoreSystemsBeforeVariant(sessionId, variant);
                    sessionService.restoreTimeBeforeVariant(sessionId, variant);
                    restorePlayerState(sessionId, variant.getBeforePlayerStateJson());
                });
        deleteTimelineFrom(sessionId, messageId);
    }

    @Transactional
    public WorldRoomTurn reroll(Long sessionId, Long messageId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        var target = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .filter(item -> item.getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reroll target must be a character message. messageId=" + messageId
                ));
        requireLatestMessage(target, "reroll");
        var precedingMessage = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(sessionId, target.getId())
                .orElse(null);

        var previousVariant = target.getContent();
        var selected = selectedVariant(target);
        sessionService.restoreCastBeforeVariant(sessionId, selected);
        sessionService.restoreSystemsBeforeVariant(sessionId, selected);
        sessionService.restoreTimeBeforeVariant(sessionId, selected);
        restorePlayerState(sessionId, selected.getBeforePlayerStateJson());
        var context = contextBuilder.buildSessionBeforeMessage(sessionId, target.getId());
        var rerollBase = precedingMessage != null && precedingMessage.getRole() == ChatRole.USER
                ? precedingMessage.getContent()
                : "[Continue the current scene naturally for one turn. The player character may actively participate: give them plausible small actions, immediate reactions, observations, movement, or brief dialogue consistent with their PlayerProfile, established relationships, and the current situation. This mode is used when the user wants the story to move forward without deciding the next beat themselves. Keep major decisions, irreversible actions, major relationship changes, high-risk commitments, and other consequential choices open for the user.]";
        var rerollPrompt = rerollBase
                + "\n\n[Previous response variant]\n"
                + previousVariant
                + "\n\nGenerate another natural variant of this response without copying it.";
        var beforeCast = sessionService.captureCastState(sessionId);
        var beforeSystems = sessionService.captureSystemState(sessionId);
        var beforeTime = sessionService.captureSimulatedAt(sessionId);
        var beforePlayerState = capturePlayerState(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, rerollPrompt);
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        applySceneLocation(sessionId, generated);
        applyNarrativeElapsedTime(sessions.findById(sessionId).orElseThrow(), generated.elapsedSeconds());
        var afterCast = sessionService.captureCastState(sessionId);
        var afterSystems = sessionService.captureSystemState(sessionId);

        int variantNo = Math.toIntExact(variants.countByMessageId(messageId)) + 1;
        var variant = variants.save(new WorldRoomMessageVariant(
                target,
                variantNo,
                generated.reply(),
                now()
        ));
        sessionService.saveCastSnapshot(variant, beforeCast, afterCast);
        sessionService.saveSystemSnapshot(variant, beforeSystems, afterSystems);
        sessionService.saveTimeSnapshot(variant, beforeTime, sessionService.captureSimulatedAt(sessionId));
        savePlayerStateSnapshot(variant, beforePlayerState, sessionId);
        target.selectVariant(variantNo, generated.reply());

        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    }

    @Transactional
    public WorldRoomTurn regenerate(Long sessionId, Long messageId) {
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        var target = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .filter(item -> item.getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Regeneration target must be a character message. messageId=" + messageId
                ));
        var userMessage = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(sessionId, target.getId())
                .filter(item -> item.getRole() == ChatRole.USER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User message for regeneration not found. messageId=" + messageId
                ));

        var selected = selectedVariant(target);
        sessionService.restoreCastBeforeVariant(sessionId, selected);
        sessionService.restoreSystemsBeforeVariant(sessionId, selected);
        sessionService.restoreTimeBeforeVariant(sessionId, selected);
        restorePlayerState(sessionId, selected.getBeforePlayerStateJson());
        deleteTimelineFrom(sessionId, target.getId());
        messages.flush();

        var context = contextBuilder.buildSession(sessionId);
        var beforeCast = sessionService.captureCastState(sessionId);
        var beforeSystems = sessionService.captureSystemState(sessionId);
        var beforeTime = sessionService.captureSimulatedAt(sessionId);
        var beforePlayerState = capturePlayerState(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, userMessage.getContent());
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        applySceneLocation(sessionId, generated);
        applyNarrativeElapsedTime(session, generated.elapsedSeconds());
        var afterCast = sessionService.captureCastState(sessionId);
        var afterSystems = sessionService.captureSystemState(sessionId);

        var characterMessage = messages.save(new WorldRoomMessage(
                session,
                ChatRole.CHARACTER,
                generated.reply(),
                now()
        ));
        var variant = variants.save(new WorldRoomMessageVariant(
                characterMessage,
                1,
                generated.reply(),
                now()
        ));
        sessionService.saveCastSnapshot(variant, beforeCast, afterCast);
        sessionService.saveSystemSnapshot(variant, beforeSystems, afterSystems);
        sessionService.saveTimeSnapshot(variant, beforeTime, session.getSimulatedAt());
        savePlayerStateSnapshot(variant, beforePlayerState, sessionId);

        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    }

    @Transactional(readOnly = true)
    public List<WorldRoomMessageVariant> variants(Long sessionId, Long messageId) {
        var message = requireCharacterMessage(sessionId, messageId);
        return variants.findByMessageIdOrderByVariantNoAsc(message.getId());
    }

    @Transactional
    public WorldRoomMessage selectVariant(Long sessionId, Long messageId, int variantNo) {
        var message = requireCharacterMessage(sessionId, messageId);
        requireLatestMessage(message, "select variant");
        var variant = variants.findByMessageIdAndVariantNo(messageId, variantNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room message variant not found. messageId=" + messageId
                                + ", variantNo=" + variantNo
                ));
        sessionService.restoreCastAfterVariant(sessionId, variant);
        sessionService.restoreSystemsAfterVariant(sessionId, variant);
        sessionService.restoreTimeAfterVariant(sessionId, variant);
        restorePlayerState(sessionId, variant.getAfterPlayerStateJson());
        message.selectVariant(variant.getVariantNo(), variant.getContent());
        return message;
    }

    @Transactional(readOnly = true)
    public List<WorldRoomMessage> history(Long sessionId, Long beforeId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));

        var page = beforeId == null
                ? messages.findTop15BySessionIdOrderByIdDesc(sessionId)
                : messages.findTop15BySessionIdAndIdLessThanOrderByIdDesc(sessionId, beforeId);

        return page.stream()
                .sorted(Comparator.comparing(WorldRoomMessage::getId))
                .toList();
    }

    private void deleteTimelineFrom(Long sessionId, Long messageId) {
        var derivedMemories = memories.findByWorldRoomSessionIdAndSourceWorldRoomMessageIdGreaterThanEqual(
                sessionId,
                messageId
        );
        if (!derivedMemories.isEmpty()) {
            var memoryIds = derivedMemories.stream()
                    .map(com.example.projectmm.domain.memory.Memory::getId)
                    .toList();
            memoryDetails.deleteByMemoryIdIn(memoryIds);
            memories.deleteAll(derivedMemories);
            memories.flush();
        }

        systemSnapshots.deleteByVariantMessageSessionIdAndVariantMessageIdGreaterThanEqual(
                sessionId,
                messageId
        );
        castSnapshots.deleteByVariantMessageSessionIdAndVariantMessageIdGreaterThanEqual(
                sessionId,
                messageId
        );
        variants.deleteByMessageSessionIdAndMessageIdGreaterThanEqual(sessionId, messageId);
        messages.deleteBySessionIdAndIdGreaterThanEqual(sessionId, messageId);
    }

    private void requireLatestMessage(WorldRoomMessage message, String action) {
        if (messages.existsBySessionIdAndIdGreaterThan(
                message.getSession().getId(),
                message.getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot " + action + " a finalized room message. messageId=" + message.getId()
            );
        }
    }

    private WorldRoomMessageVariant selectedVariant(WorldRoomMessage message) {
        var selectedVariantNo = message.getSelectedVariantNo();
        if (selectedVariantNo == null) {
            throw new IllegalStateException(
                    "Selected variant not found. messageId=" + message.getId()
            );
        }
        return variants.findByMessageIdAndVariantNo(message.getId(), selectedVariantNo)
                .orElseThrow(() -> new IllegalStateException(
                        "Selected variant not found. messageId=" + message.getId()
                                + ", variantNo=" + selectedVariantNo
                ));
    }

    private WorldRoomMessage requireCharacterMessage(Long sessionId, Long messageId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        return messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .filter(item -> item.getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Character message not found. messageId=" + messageId
                ));
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
