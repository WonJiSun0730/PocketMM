package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Advisory continuity review; never replaces or rejects the generated conversation. */
@Service
public class SceneContinuityService {
    private final DialogueGateway gateway;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ChatFailureStore failureStore;

    public SceneContinuityService(DialogueGateway gateway,WorldRoomMessageRepository messages,
                                  WorldRoomMessageVariantRepository variants) {
        this(gateway,messages,variants,(ChatFailureStore)null);
    }
    @Autowired
    public SceneContinuityService(DialogueGateway gateway,WorldRoomMessageRepository messages,
                                  WorldRoomMessageVariantRepository variants,
                                  org.springframework.beans.factory.ObjectProvider<ChatFailureStore> failureStore) {
        this(gateway,messages,variants,failureStore.getIfAvailable());
    }
    public SceneContinuityService(DialogueGateway gateway,WorldRoomMessageRepository messages,
                                  WorldRoomMessageVariantRepository variants,ChatFailureStore failureStore) {
        this.gateway=gateway;this.messages=messages;this.variants=variants;this.failureStore=failureStore;
    }


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
        if(!SceneContinuityObservation.enabled())return SceneContinuityObservation.observe(()->{
            var outcome=generate(context,modelInput,userInput,beforeId);
            if(failureStore!=null)for(var finding:SceneContinuityObservation.current()) {
                var evidence=ChatFailureStore.evidence(new ReviewRejectedException(finding.review(),finding.mode()),
                        UUID.randomUUID().toString(),context.sessionId(),"ADVISORY",outcome.turn().reply());
                try {failureStore.save(evidence);}
                catch(java.io.IOException|RuntimeException failedSave) {
                    org.slf4j.LoggerFactory.getLogger(SceneContinuityService.class).warn("Continuity advisory save failed session={}",context.sessionId());
                }
            }
            return outcome;
        });
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
        return observeCandidate(enriched,before,beforeJson,userInput,candidate);
    }

    private Outcome observeCandidate(WorldRoomDialogueContext context,List<SceneFacts.Fact> before,
                                     String beforeJson,String user,WorldRoomTurn candidate) {
        var reviewed=advisoryReview(context,"REVIEW",before,user,candidate,List.of());
        var after=applyAdvisory(before,reviewed,user,candidate.reply(),"","REVIEW");
        return new Outcome(candidate,context,beforeJson,JSON.writeValueAsString(after));
    }

    private Review advisoryReview(WorldRoomDialogueContext context,String mode,List<SceneFacts.Fact> facts,
                                  String user,WorldRoomTurn candidate,List<Evidence> history) {
        try {
            var result=review(context,mode,facts,user,candidate,history);
            if(result==null || !result.approved() || result.changes()==null || result.conflicts()==null || !result.conflicts().isEmpty())
                SceneContinuityObservation.record(mode,"REJECTED","Advisory finding; conversation continues",result);
            else if(result.turn()!=null && !result.turn().equals(candidate))
                SceneContinuityObservation.record(mode,"REPAIR_PROPOSED","Original conversation preserved",result);
            return result;
        } catch(ReviewFormatException malformed) {
            SceneContinuityObservation.record(mode,"INVALID_JSON",malformed.getMessage(),null);
            return null;
        }
    }

    private List<SceneFacts.Fact> applyAdvisory(List<SceneFacts.Fact> before,Review review,
                                              String user,String reply,String history,String mode) {
        if(review==null || review.changes()==null)return before;
        var after=before;
        // Approval is advisory. Validate each delta against the actual committed text,
        // preserving valid changes even if a different delta or the verdict is wrong.
        var touched=new HashSet<String>();
        for(var change:review.changes()) {
            try {
                if(change==null || !touched.add(change.key()))throw new IllegalStateException("Repeated or missing fact change");
                after=SceneFacts.apply(after,List.of(change),user,reply,history);
            } catch(IllegalStateException invalid) {
                SceneContinuityObservation.record(mode,"INVALID_FACTS",invalid.getMessage(),review);
            }
        }
        return after;
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
            var boot=advisoryReview(context.withSceneFacts(JSON.writeValueAsString(facts)),
                    "BOOTSTRAP",facts,userEvidence,null,history);
            facts=applyAdvisory(facts,boot,userEvidence,"",historyEvidence,"BOOTSTRAP");
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
