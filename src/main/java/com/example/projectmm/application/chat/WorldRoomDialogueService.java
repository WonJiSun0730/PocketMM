package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.DialogueTiming;
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
@org.springframework.context.annotation.Import(com.example.projectmm.application.dialogue.SceneContinuityService.class)
public class WorldRoomDialogueService {

    private String captureTestMemorySnapshot(Long sessionId) {
        var inspector = testMemoryInspector.getIfAvailable();
        return inspector == null ? null : objectMapper.writeValueAsString(inspector.snapshotAll(sessionId));
    }

    private static String toTestDiagnostics(com.example.projectmm.infrastructure.dialogue.AiUsageTracker.Snapshot usage) {
        if (usage == null) return null;
        return objectMapper.writeValueAsString(usage);
    }

    private final WorldRoomContextBuilder contextBuilder;
    private final DialogueGateway dialogueGateway;
    private final com.example.projectmm.application.dialogue.SceneContinuityService continuity;
    private final org.springframework.beans.factory.ObjectProvider<com.example.projectmm.application.memory.PocketMemoryInspectorService> testMemoryInspector;
    private final WorldRoomSessionService sessionService;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomPlayerStateRepository playerStates;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @org.springframework.beans.factory.annotation.Value("${gemini.model:gemini-3.5-flash-lite}")
    private String evaluationGeminiModel = "gemini-3.5-flash-lite";
    @org.springframework.beans.factory.annotation.Value("${openai.model:gpt-5.6-luna}")
    private String evaluationOpenAiModel = "gpt-5.6-luna";

    private String captureEvaluationContext(String operation, String input, String modelInput, String reply,
            com.example.projectmm.domain.dialogue.WorldRoomDialogueContext context) {
        String model = context.aiModel();
        if (model == null || model.isBlank()) model = "OPENAI".equalsIgnoreCase(context.aiProvider())
                ? evaluationOpenAiModel : evaluationGeminiModel;
        return com.example.projectmm.application.dialogue.DialogueEvaluationEvidence
                .capture(operation, input, modelInput, reply, model, context);
    }
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final WorldRoomVariantCastSnapshotRepository castSnapshots;
    private final WorldRoomVariantSystemSnapshotRepository systemSnapshots;
    private final ApplicationEventPublisher events;
    private final MemoryRepository memories;
    private final ProtectedMemoryDetailRepository memoryDetails;
    private final TimeInterpreter timeInterpreter;
    private final com.example.projectmm.domain.chat.PocketActivityReportRepository activityReports;

    @Transactional
    public WorldRoomTurn chat(Long sessionId, String userMessage) {
        return com.example.projectmm.infrastructure.dialogue.AiUsageTracker.withScope(sessionId, "ROLEPLAY", () -> {
        var session = DialogueTiming.stage("SESSION_LOCK",()->sessionService.lockForDialogue(sessionId));

        DialogueTiming.stage("INITIAL_SCENE_SETUP",()->{initializeSceneIfNeeded(session, userMessage);return null;});
        var userSourceMessage = DialogueTiming.stage("USER_MESSAGE_SAVE",()->messages.save(new WorldRoomMessage(
                session,
                ChatRole.USER,
                userMessage,
                now()
        )));

        var beforeTime = session.getSimulatedAt();
        var beforePlayerState = DialogueTiming.stage("PLAYER_STATE_SNAPSHOT",()->capturePlayerState(sessionId));
        String sceneSetup = DialogueTiming.stage("SCENE_INSTRUCTION",()->applySceneInstruction(session, userMessage, null));
        boolean explicitTimeChanged = DialogueTiming.stage("TIME_INTERPRETATION",()->sceneSetupHasTime(sceneSetup) || applyExplicitTimeChange(session, userMessage));
        var context = DialogueTiming.stage("CONTEXT_BUILD",()->contextBuilder.buildSession(sessionId));
        var beforeCast = DialogueTiming.stage("STATE_SNAPSHOT",()->sessionService.captureCastState(sessionId));
        var beforeSystems = DialogueTiming.stage("STATE_SNAPSHOT",()->sessionService.captureSystemState(sessionId));
        var measured = com.example.projectmm.infrastructure.dialogue.AiUsageTracker.capture(sessionId, () -> continuity.generate(context, userMessage, userMessage, userSourceMessage.getId()));
        var checked = measured.value();
        var generated = checked.turn();
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        applySceneLocation(sessionId, generated);
        if (!explicitTimeChanged && sceneSetup == null) {
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
        variant.captureSceneFactsWorldline(checked.before(), checked.after());
        variant.captureUserSceneSetup(sceneSetup);

        variant.captureTestDiagnostics(toTestDiagnostics(measured.usage()));
        variant.captureTestMemoryBefore(captureTestMemorySnapshot(sessionId));
        variant.captureTestMemoryAfter(captureTestMemorySnapshot(sessionId));
        variant.captureEvaluationContext(captureEvaluationContext("CHAT", userMessage, userMessage, generated.reply(), checked.context()));
        events.publishEvent(new PocketUserSpeechCommitted(
                sessionId,
                userSourceMessage.getId(),
                userMessage,
                context.simulatedAt(),
                context.activeCast().stream().map(character -> character.characterId()).toList()
        ));

        session.markPresent(now());
        session.markProgressed(now());
        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    
        });
    }

    private record PlayerStateSnapshot(String location, List<String> conditions, List<String> inventory) {}

    private record SceneSetupSnapshot(String source, com.example.projectmm.domain.dialogue.WorldRoomInitialScene scene) {}

    private static final java.util.regex.Pattern STATE_INSTRUCTION = java.util.regex.Pattern.compile(
            "(?i)\\booc\\b|(?:롤플|롤플레이).*(?:중단|다시|새)|(?:새\\s*(?:상황|장면)).*(?:시작|설정)|(?:장소|소지품|상태|시간|날짜).*(?:만\\s*(?:바꿔|변경)|설정해|수정해)");

    private String applySceneInstruction(com.example.projectmm.domain.chat.WorldRoomSession session, String input, String cached) {
        if (input == null || !STATE_INSTRUCTION.matcher(input).find()) return null;
        com.example.projectmm.domain.dialogue.WorldRoomInitialScene proposed = null;
        if (cached != null) {
            try {
                var previous = objectMapper.readValue(cached, SceneSetupSnapshot.class);
                if (input.equals(previous.source())) proposed = previous.scene();
            } catch (Exception invalidCache) { /* Request a fresh analysis when a cache is invalid. */ }
        }
        if (proposed == null) {
            String reply = dialogueGateway.setupWorldRoomScene(contextBuilder.buildSession(session.getId()), input);
            if (reply == null || reply.isBlank()) return null;
            try {
                proposed = objectMapper.readValue(reply, com.example.projectmm.domain.dialogue.WorldRoomInitialScene.class);
            } catch (Exception invalidSetup) { return null; }
        }
        if (proposed == null || !("PATCH".equals(proposed.mode()) || "RESET".equals(proposed.mode()))) return null;
        var state = playerStates.findBySessionId(session.getId())
                .orElseGet(() -> playerStates.save(new WorldRoomPlayerState(session)));
        boolean reset = "RESET".equals(proposed.mode());
        String location = proposed.location();
        if (location == null || location.isBlank() || location.trim().length() > 200) location = state.getLocation();
        var inventory = proposed.inventory() == null && !reset ? List.copyOf(state.getInventory()) : validSceneItems(proposed.inventory());
        var conditions = proposed.conditions() == null && !reset ? List.copyOf(state.getConditions()) : validSceneItems(proposed.conditions());
        state.update(location, conditions, inventory);
        if (proposed.simulatedAt() != null) session.updateSimulatedAt(proposed.simulatedAt().withSecond(0).withNano(0));
        return objectMapper.writeValueAsString(new SceneSetupSnapshot(input, proposed));
    }

    private List<String> validSceneItems(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank() && value.trim().length() <= 300)
                .map(String::trim).distinct().limit(50).toList();
    }

    private boolean sceneSetupHasTime(String setup) {
        if (setup == null) return false;
        return objectMapper.readValue(setup, SceneSetupSnapshot.class).scene().simulatedAt() != null;
    }

    private void initializeSceneIfNeeded(com.example.projectmm.domain.chat.WorldRoomSession session, String openingMessage) {
        if (!Boolean.FALSE.equals(session.getInitialSceneResolved())) return;
        String reply = dialogueGateway.initializeWorldRoomScene(contextBuilder.buildSession(session.getId()),
                !Boolean.TRUE.equals(session.getInitialTimeConfigured()), openingMessage);
        com.example.projectmm.domain.dialogue.WorldRoomInitialScene proposed = null;
        if (reply != null && !reply.isBlank()) {
            try {
                proposed = objectMapper.readValue(reply, com.example.projectmm.domain.dialogue.WorldRoomInitialScene.class);
            } catch (Exception invalidSetup) {
                // Keep the supplied initial values and provisional time when AI setup is malformed.
            }
        }
        session.completeInitialScene(proposed);
        var state = playerStates.findBySessionId(session.getId())
                .orElseGet(() -> playerStates.save(new WorldRoomPlayerState(session)));
        state.update(session.getInitialLocation(), session.getInitialConditions(), session.getInitialInventory());
    }

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
        // An exact current-time confirmation can legitimately leave the clock unchanged.
        // It still establishes the endpoint of this turn, so never append AI elapsed time.
        return interpretation.precision() == com.example.projectmm.domain.time.AiTimeParseResult.TimePrecision.EXACT
                && change.expectedElapsedMinutes() != null && change.expectedElapsedMinutes() == 0L;
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
        return com.example.projectmm.infrastructure.dialogue.AiUsageTracker.withScope(sessionId, "ROLEPLAY", () -> {
        var session = sessionService.lockForDialogue(sessionId);

        initializeSceneIfNeeded(session, null);
        var context = contextBuilder.buildSession(sessionId);
        var beforeCast = sessionService.captureCastState(sessionId);
        var beforeSystems = sessionService.captureSystemState(sessionId);
        var beforeTime = session.getSimulatedAt();
        int continuationNo = messages.findTopBySessionIdOrderByIdDesc(sessionId)
                .map(WorldRoomMessage::getAutomaticContinuationNo)
                .map(previous -> Math.addExact(previous, 1)).orElse(1);
        var continuationPrompt = com.example.projectmm.application.dialogue.WorldRoomContinuationPrompt
                .build(session.isNoControlPlayer(), continuationNo);
        var beforePlayerState = capturePlayerState(sessionId);
        var measured = com.example.projectmm.infrastructure.dialogue.AiUsageTracker.capture(sessionId, () -> continuity.generate(context, continuationPrompt, "", Long.MAX_VALUE));
        var checked = measured.value();
        var generated = checked.turn();
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
        characterMessage.markAutomaticContinuation(continuationNo);
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
        variant.captureSceneFactsWorldline(checked.before(), checked.after());

        variant.captureTestDiagnostics(toTestDiagnostics(measured.usage()));
        variant.captureTestMemoryBefore(captureTestMemorySnapshot(sessionId));
        variant.captureTestMemoryAfter(captureTestMemorySnapshot(sessionId));
        variant.captureEvaluationContext(captureEvaluationContext("CONTINUE", "[자동 전개]", continuationPrompt, generated.reply(), checked.context()));

        session.markPresent(now());
        session.markProgressed(now());
        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    
        });
    }

    @Transactional
    public WorldRoomMessage editMessage(Long sessionId, Long messageId, String content) {
        sessionService.lockForDialogue(sessionId);
        var message = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room message not found. messageId=" + messageId
                ));
        requireLiveReply(message);
        // Earlier edits have downstream worldlines; only the latest reply can be
        // re-evaluated without invalidating subsequent snapshots.
        boolean latestCharacter = message.getRole() == ChatRole.CHARACTER
                && !messages.existsBySessionIdAndIdGreaterThan(sessionId, messageId);
        var variant = message.getRole() == ChatRole.CHARACTER ? selectedVariant(message) : null;
        if (latestCharacter && variant.getBeforePlayerStateJson() != null) {
            var currentState = capturePlayerState(sessionId);
            try {
                restorePlayerState(sessionId, variant.getBeforePlayerStateJson());
                var context = contextBuilder.buildSessionBeforeMessage(sessionId, messageId);
                var analyzed = dialogueGateway.analyzeEditedWorldRoomState(context, content);
                if (analyzed != null) {
                    applySceneLocation(sessionId, analyzed);
                    savePlayerStateSnapshot(variant, variant.getBeforePlayerStateJson(), sessionId);
                } else {
                    restorePlayerState(sessionId, currentState);
                }
            } catch (Exception extractionFailure) {
                // A model or extraction error must never prevent saving the edited prose.
                restorePlayerState(sessionId, currentState);
            }
        }
        variants.invalidateSceneFactsFrom(sessionId, messageId);
        message.edit(content, now());
        if (variant != null) {
            variant.editContent(content);
        }
        return message;
    }

    @Transactional
    public void deleteMessageAndFollowing(Long sessionId, Long messageId) {
        sessionService.lockForDialogue(sessionId);
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
        return com.example.projectmm.infrastructure.dialogue.AiUsageTracker.withScope(sessionId, "ROLEPLAY", () -> {
        var rerollSession = sessionService.lockForDialogue(sessionId);
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
        var rerollBase = target.getAutomaticContinuationNo() == null
                && precedingMessage != null && precedingMessage.getRole() == ChatRole.USER
                ? precedingMessage.getContent()
                : com.example.projectmm.application.dialogue.WorldRoomContinuationPrompt.build(
                        rerollSession.isNoControlPlayer(),
                        target.getAutomaticContinuationNo() == null ? 1 : target.getAutomaticContinuationNo());
        var rerollPrompt = rerollBase
                + "\n\n[Previous response variant]\n"
                + previousVariant
                + "\n\nGenerate another natural variant of this response without copying it.";
        var beforeCast = sessionService.captureCastState(sessionId);
        var beforeSystems = sessionService.captureSystemState(sessionId);
        var beforeTime = sessionService.captureSimulatedAt(sessionId);
        var beforePlayerState = capturePlayerState(sessionId);
        String sceneSetup = applySceneInstruction(rerollSession, rerollBase, selected.getUserSceneSetupJson());
        boolean explicitTimeChanged = sceneSetupHasTime(sceneSetup)
                || (precedingMessage != null && precedingMessage.getRole() == ChatRole.USER
                && applyExplicitTimeChange(rerollSession, rerollBase));
        var context = contextBuilder.buildSessionBeforeMessage(sessionId, target.getId());
        var measured = com.example.projectmm.infrastructure.dialogue.AiUsageTracker.capture(sessionId, () -> continuity.generate(context, rerollPrompt, target.getAutomaticContinuationNo() == null ? rerollBase : "",
                target.getAutomaticContinuationNo() == null && precedingMessage != null && precedingMessage.getRole() == ChatRole.USER
                        ? precedingMessage.getId() : target.getId()));
        var checked = measured.value();
        var generated = checked.turn();
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        applySceneLocation(sessionId, generated);
        if (!explicitTimeChanged && sceneSetup == null) {
            applyNarrativeElapsedTime(rerollSession, generated.elapsedSeconds());
        }
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
        variant.captureSceneFactsWorldline(checked.before(), checked.after());
        variant.captureUserSceneSetup(sceneSetup);
        target.selectVariant(variantNo, generated.reply());

        variant.captureTestDiagnostics(toTestDiagnostics(measured.usage()));
        variant.captureTestMemoryBefore(captureTestMemorySnapshot(sessionId));
        variant.captureTestMemoryAfter(captureTestMemorySnapshot(sessionId));
        variant.captureEvaluationContext(captureEvaluationContext("REROLL", rerollBase, rerollPrompt, generated.reply(), checked.context()));

        rerollSession.markPresent(now());
        rerollSession.markProgressed(now());
        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    
        });
    }

    @Transactional
    public WorldRoomTurn regenerate(Long sessionId, Long messageId) {
        return com.example.projectmm.infrastructure.dialogue.AiUsageTracker.withScope(sessionId, "ROLEPLAY", () -> {
        var session = sessionService.lockForDialogue(sessionId);
        var target = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .filter(item -> item.getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Regeneration target must be a character message. messageId=" + messageId
                ));
        requireLiveReply(target);
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

        var beforeCast = sessionService.captureCastState(sessionId);
        var beforeSystems = sessionService.captureSystemState(sessionId);
        var beforeTime = sessionService.captureSimulatedAt(sessionId);
        var beforePlayerState = capturePlayerState(sessionId);
        String sceneSetup = applySceneInstruction(session, userMessage.getContent(), selected.getUserSceneSetupJson());
        boolean explicitTimeChanged = sceneSetupHasTime(sceneSetup) || applyExplicitTimeChange(session, userMessage.getContent());
        var context = contextBuilder.buildSession(sessionId);
        var measured = com.example.projectmm.infrastructure.dialogue.AiUsageTracker.capture(sessionId, () -> continuity.generate(context, userMessage.getContent(), userMessage.getContent(), userMessage.getId()));
        var checked = measured.value();
        var generated = checked.turn();
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        applySceneLocation(sessionId, generated);
        if (!explicitTimeChanged && sceneSetup == null) {
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
        variant.captureSceneFactsWorldline(checked.before(), checked.after());
        variant.captureUserSceneSetup(sceneSetup);

        variant.captureTestDiagnostics(toTestDiagnostics(measured.usage()));
        variant.captureTestMemoryBefore(captureTestMemorySnapshot(sessionId));
        variant.captureTestMemoryAfter(captureTestMemorySnapshot(sessionId));
        variant.captureEvaluationContext(captureEvaluationContext("REGENERATE", userMessage.getContent(), userMessage.getContent(), generated.reply(), checked.context()));

        session.markPresent(now());
        session.markProgressed(now());
        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    
        });
    }

    @Transactional(readOnly = true)
    public List<WorldRoomMessageVariant> variants(Long sessionId, Long messageId) {
        var message = requireCharacterMessage(sessionId, messageId);
        return variants.findByMessageIdOrderByVariantNoAsc(message.getId());
    }

    @Transactional
    public WorldRoomMessage selectVariant(Long sessionId, Long messageId, int variantNo) {
        sessionService.lockForDialogue(sessionId);
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
        boolean removedActivity = activityReports.findTopBySessionIdOrderByIdDesc(sessionId)
                .map(report -> report.getSourceMessageId() != null && report.getSourceMessageId() >= messageId).orElse(false);
        activityReports.deleteBySessionIdAndSourceMessageIdGreaterThanEqual(sessionId, messageId);
        if (removedActivity) sessions.findById(sessionId).orElseThrow().clearProgressPause();
        var derivedMemories = memories.findByWorldRoomSessionIdAndSourceWorldRoomMessageIdGreaterThanEqual(
                sessionId,
                messageId
        );
        if (!derivedMemories.isEmpty()) {
            var memoryIds = derivedMemories.stream()
                    .map(com.example.projectmm.domain.memory.Memory::getId)
                    .toList();
            memories.deleteWithDependencies(memoryIds);
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
        requireLiveReply(message);
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

    private void requireLiveReply(WorldRoomMessage message) {
        if (message.getAbsenceReportId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "부재 활동 기록은 수정하거나 재생성할 수 없어요.");
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
