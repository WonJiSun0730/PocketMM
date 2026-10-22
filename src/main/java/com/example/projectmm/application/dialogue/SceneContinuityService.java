package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Gate before any generated prose, state delta or derived memory can be committed. */
@Service
@RequiredArgsConstructor
public class SceneContinuityService {
    private final DialogueGateway gateway;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Review(boolean approved, WorldRoomTurn turn, List<SceneFacts.Change> changes, List<String> conflicts) {}
    public record Outcome(WorldRoomTurn turn, WorldRoomDialogueContext context, String before, String after) {}

    public Outcome generate(WorldRoomDialogueContext context, String modelInput, String userInput, long beforeId) {
        List<SceneFacts.Fact> before = prepare(context, beforeId);
        String beforeJson = JSON.writeValueAsString(before);
        var enriched = context.withSceneFacts(beforeJson);
        var candidate = gateway.generateWorldRoomTurn(enriched, modelInput);
        requireTurn(candidate, enriched);
        var reviewed = review(enriched, "REVIEW", before, userInput, candidate, List.of());
        requireReview(reviewed);
        if (reviewed.turn() == null) reviewed = new Review(true, candidate, reviewed.changes(), reviewed.conflicts());
        requireTurn(reviewed.turn(), enriched);
        // A repaired response must pass a second independent check; no unbounded repair loop.
        if (!candidate.equals(reviewed.turn())) {
            var verified = review(enriched, "VERIFY", before, userInput, reviewed.turn(), List.of());
            requireReview(verified);
            if (verified.turn() == null) verified = new Review(true, reviewed.turn(), verified.changes(), verified.conflicts());
            if (!reviewed.turn().equals(verified.turn()))
                throw new IllegalStateException("Scene continuity verification changed the response again");
            reviewed = verified;
        }
        var after = SceneFacts.apply(before, reviewed.changes(), userInput, reviewed.turn().reply(), "");
        return new Outcome(reviewed.turn(), enriched, beforeJson, JSON.writeValueAsString(after));
    }

    private List<SceneFacts.Fact> prepare(WorldRoomDialogueContext context, long beforeId) {
        if (context.sessionId() == null) throw new IllegalStateException("Scene continuity needs a session");
        var checkpoints = variants.selectedSceneCheckpoints(context.sessionId(), beforeId, PageRequest.of(0, 1));
        long afterId = 0;
        List<SceneFacts.Fact> facts = List.of();
        if (!checkpoints.isEmpty()) {
            var checkpoint = checkpoints.getFirst();
            facts = readFacts(checkpoint.getAfterSceneFactsJson());
            afterId = checkpoint.getMessage().getId();
        }
        // Backfill committed history once for legacy pockets, edits and absence activities.
        // Page by message ID; no historical fact is silently lost at the six-turn window.
        while (true) {
            var batch = messages.sceneHistoryAfter(context.sessionId(), afterId, beforeId, PageRequest.of(0, 12));
            if (batch.isEmpty()) break;
            var history = batch.stream().map(m -> new Evidence(m.getRole().name(), m.getContent())).toList();
            String userEvidence = batch.stream().filter(m -> m.getRole() == ChatRole.USER)
                    .map(WorldRoomMessage::getContent).collect(java.util.stream.Collectors.joining("\n"));
            String historyEvidence = batch.stream().map(WorldRoomMessage::getContent)
                    .collect(java.util.stream.Collectors.joining("\n"));
            var boot = review(context.withSceneFacts(JSON.writeValueAsString(facts)),
                    "BOOTSTRAP", facts, userEvidence, null, history);
            requireReview(boot);
            if (boot.turn() != null) throw new IllegalStateException("Scene bootstrap must not generate a turn");
            facts = SceneFacts.apply(facts, boot.changes(), userEvidence, "", historyEvidence);
            afterId = batch.getLast().getId();
        }
        return facts;
    }

    private record Evidence(String role, String content) {}
    private Review review(WorldRoomDialogueContext context, String mode, List<SceneFacts.Fact> facts,
                          String user, WorldRoomTurn candidate, List<Evidence> history) {
        var data = new LinkedHashMap<String, Object>();
        data.put("mode", mode);
        data.put("facts", facts);
        data.put("userInput", user == null ? "" : user);
        data.put("candidate", candidate);
        data.put("history", history);
        String result = com.example.projectmm.infrastructure.dialogue.AiUsageTracker.withPurpose(
                context.sessionId(), "SCENE_CONTINUITY",
                () -> gateway.reviewWorldRoomContinuity(context, JSON.writeValueAsString(data)));
        if (result == null || result.isBlank()) throw new IllegalStateException("Scene continuity review unavailable");
        try {
            return JSON.readValue(result, Review.class);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Invalid scene continuity review", invalid);
        }
    }

    public static List<SceneFacts.Fact> readFacts(String json) {
        if (json == null || json.isBlank()) return List.of();
        var facts = Arrays.asList(JSON.readValue(json, SceneFacts.Fact[].class));
        // Validate persisted data as well, without mutating it.
        return SceneFacts.apply(facts, List.of(), "", "", "");
    }

    private void requireReview(Review review) {
        if (review == null || !review.approved() || review.changes() == null || review.conflicts() == null
                || !review.conflicts().isEmpty())
            throw new IllegalStateException("Scene continuity review did not approve the response");
    }

    private void requireTurn(WorldRoomTurn turn, WorldRoomDialogueContext context) {
        if (turn == null || turn.reply() == null || turn.reply().isBlank() || turn.elapsedSeconds() > 604800)
            throw new IllegalStateException("Invalid continuity-reviewed turn");
        var castIds = context.roomCast().stream().map(WorldRoomCharacterContext::characterId)
                .collect(java.util.stream.Collectors.toSet());
        if (!castIds.containsAll(turn.castChange().enterCharacterIds())
                || !castIds.containsAll(turn.castChange().leaveCharacterIds())
                || turn.castChange().enterCharacterIds().stream().anyMatch(turn.castChange().leaveCharacterIds()::contains))
            throw new IllegalStateException("Invalid continuity-reviewed cast");
        var systemIds = context.sessionSystems().stream().map(WorldRoomSystemContext::systemId)
                .collect(java.util.stream.Collectors.toSet());
        var seen = new HashSet<Long>();
        for (var change : turn.systemChanges()) {
            if (!systemIds.contains(change.systemId()) || !seen.add(change.systemId()))
                throw new IllegalStateException("Invalid continuity-reviewed system delta");
        }
        for (var list : List.of(turn.addedConditions(), turn.removedConditions(),
                turn.addedInventory(), turn.removedInventory())) {
            if (list.size() > 50 || list.stream().anyMatch(s -> s.isBlank() || s.length() > 300))
                throw new IllegalStateException("Invalid continuity-reviewed player state");
        }
        if (turn.locationChange() != null && turn.locationChange().length() > 200)
            throw new IllegalStateException("Invalid continuity-reviewed location");
    }
}
