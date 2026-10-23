package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Production approval gate; explicitly scoped QA observes errors while preserving target output. */
@Service
@RequiredArgsConstructor
public class SceneContinuityService {
    private final DialogueGateway gateway;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Review(boolean approved, WorldRoomTurn turn, List<SceneFacts.Change> changes, List<String> conflicts) {}
    public record Outcome(WorldRoomTurn turn, WorldRoomDialogueContext context, String before, String after) {}

    public static final class ReviewRejectedException extends IllegalStateException {
        private final Review review;
        private final String mode;
        private final WorldRoomTurn candidate;
        public ReviewRejectedException(Review review) {
            this(review,"UNKNOWN");
        }
        public ReviewRejectedException(Review review,String mode) {
            this(review,mode,null);
        }
        public ReviewRejectedException(Review review,String mode,WorldRoomTurn candidate) {
            super("Scene continuity review did not approve the response");
            this.mode=mode;
            this.candidate=candidate;
            this.review=review;
        }
        public Review review() {return review;}
        public String mode() {return mode;}
        public WorldRoomTurn candidate() {return candidate;}
    }
    private static final class ReviewFormatException extends IllegalStateException {
        ReviewFormatException(Throwable cause) {super("Invalid scene continuity review",cause);}
    }

    public Outcome generate(WorldRoomDialogueContext context, String modelInput, String userInput, long beforeId) {
        var result=generateChecked(context,modelInput,userInput,beforeId);
        DialogueTiming.reviewed();
        return result;
    }

    private Outcome generateChecked(WorldRoomDialogueContext context, String modelInput, String userInput, long beforeId) {
        List<SceneFacts.Fact> before = DialogueTiming.stage("SCENE_FACTS_PREPARE",()->prepare(context, beforeId));
        String beforeJson = JSON.writeValueAsString(before);
        var enriched = context.withSceneFacts(beforeJson);
        DialogueTiming.generationStarting();
        var candidate = DialogueTiming.stage("TARGET_GENERATION",()->DialogueTokens.generate(()->gateway.generateWorldRoomTurn(enriched, modelInput)));
        requireTurn(candidate, enriched);
        DialogueTiming.bodyReady();
        DialoguePreview.generated(candidate.reply());
        if(SceneContinuityObservation.enabled())return observeCandidate(enriched,before,beforeJson,userInput,candidate);
        var reviewed = review(enriched, "REVIEW", before, userInput, candidate, List.of());
        requireReview(reviewed,"REVIEW",candidate);
        if (reviewed.turn() == null) reviewed = new Review(true, candidate, reviewed.changes(), reviewed.conflicts());
        requireTurn(reviewed.turn(), enriched);
        // A repaired response must pass a second independent check; no unbounded repair loop.
        if (!candidate.equals(reviewed.turn())) {
            var verified = review(enriched, "VERIFY", before, userInput, reviewed.turn(), List.of());
            requireReview(verified,"VERIFY",reviewed.turn());
            if (verified.turn() == null) verified = new Review(true, reviewed.turn(), verified.changes(), verified.conflicts());
            if (!reviewed.turn().equals(verified.turn()))
                throw new IllegalStateException("Scene continuity verification changed the response again");
            reviewed = verified;
        }
        var after = SceneFacts.apply(before, reviewed.changes(), userInput, reviewed.turn().reply(), "");
        return new Outcome(reviewed.turn(), enriched, beforeJson, JSON.writeValueAsString(after));
    }

    private Outcome observeCandidate(WorldRoomDialogueContext context,List<SceneFacts.Fact> before,
                                     String beforeJson,String user,WorldRoomTurn candidate) {
        Review reviewed;
        try {
            reviewed=review(context,"REVIEW",before,user,candidate,List.of());
            requireReview(reviewed,"REVIEW",candidate);
        } catch(ReviewRejectedException rejected) {
            SceneContinuityObservation.record("REVIEW","REJECTED",rejected.getMessage(),rejected.review());
            return new Outcome(candidate,context,beforeJson,beforeJson);
        } catch(ReviewFormatException malformed) {
            SceneContinuityObservation.record("REVIEW","INVALID_JSON",malformed.getMessage(),null);
            return new Outcome(candidate,context,beforeJson,beforeJson);
        }
        if(reviewed.turn()!=null&&!candidate.equals(reviewed.turn())) {
            SceneContinuityObservation.record("REVIEW","REPAIR_PROPOSED",
                    "QA keeps the target's original output; proposed repair and its fact changes were not applied",reviewed);
            return new Outcome(candidate,context,beforeJson,beforeJson);
        }
        try {
            var after=SceneFacts.apply(before,reviewed.changes(),user,candidate.reply(),"");
            return new Outcome(candidate,context,beforeJson,JSON.writeValueAsString(after));
        } catch(IllegalStateException invalidFacts) {
            // Pure local validation only; provider/transport/budget exceptions never enter this catch.
            SceneContinuityObservation.record("REVIEW","INVALID_FACTS",invalidFacts.getMessage(),reviewed);
            return new Outcome(candidate,context,beforeJson,beforeJson);
        }
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
            if(SceneContinuityObservation.enabled()) {
                Review boot=null;
                try {
                    boot=review(context.withSceneFacts(JSON.writeValueAsString(facts)),"BOOTSTRAP",facts,userEvidence,null,history);
                    requireReview(boot,"BOOTSTRAP",null);
                    if(boot.turn()!=null)SceneContinuityObservation.record("BOOTSTRAP","REPAIR_PROPOSED","Bootstrap must not generate a turn",boot);
                    else {
                        try {facts=SceneFacts.apply(facts,boot.changes(),userEvidence,"",historyEvidence);}
                        catch(IllegalStateException invalidFacts) {SceneContinuityObservation.record("BOOTSTRAP","INVALID_FACTS",invalidFacts.getMessage(),boot);}
                    }
                } catch(ReviewRejectedException rejected) {
                    SceneContinuityObservation.record("BOOTSTRAP","REJECTED",rejected.getMessage(),rejected.review());
                } catch(ReviewFormatException malformed) {
                    SceneContinuityObservation.record("BOOTSTRAP","INVALID_JSON",malformed.getMessage(),null);
                }
            } else {
                var boot = review(context.withSceneFacts(JSON.writeValueAsString(facts)),
                        "BOOTSTRAP", facts, userEvidence, null, history);
                requireReview(boot,"BOOTSTRAP",null);
                if (boot.turn() != null) throw new IllegalStateException("Scene bootstrap must not generate a turn");
                facts = SceneFacts.apply(facts, boot.changes(), userEvidence, "", historyEvidence);
            }
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
        if (result == null || result.isBlank()) throw new ReviewFormatException(new IllegalArgumentException("Empty review result"));
        try {
            return JSON.readValue(result, Review.class);
        } catch (RuntimeException invalid) {
            throw new ReviewFormatException(invalid);
        }
    }

    public static List<SceneFacts.Fact> readFacts(String json) {
        if (json == null || json.isBlank()) return List.of();
        var facts = Arrays.asList(JSON.readValue(json, SceneFacts.Fact[].class));
        // Validate persisted data as well, without mutating it.
        return SceneFacts.apply(facts, List.of(), "", "", "");
    }

    private void requireReview(Review review,String mode,WorldRoomTurn candidate) {
        if (review == null || !review.approved() || review.changes() == null || review.conflicts() == null
                || !review.conflicts().isEmpty())
            throw new ReviewRejectedException(review,mode,candidate);
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
