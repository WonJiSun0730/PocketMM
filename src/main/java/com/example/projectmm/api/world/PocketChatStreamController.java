package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomDialogueService;
import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.application.dialogue.DialoguePreview;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/** Flushes a provisional body while the existing transactional pipeline finishes. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worlds/{worldId}/rooms/{roomId}/sessions/{sessionId}")
public class PocketChatStreamController {
    private final WorldRoomSessionService sessions;
    private final WorldRoomDialogueService dialogue;
    private final com.example.projectmm.application.dialogue.ChatFailureStore failures;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PocketChatStreamController.class);

    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson")
    public void chat(@PathVariable Long worldId, @PathVariable Long roomId,
                     @PathVariable Long sessionId, @Valid @RequestBody WorldRoomChatRequest request,
                     Authentication authentication, HttpServletResponse response) throws IOException {
        var session = sessions.ownedSession(roomId, sessionId, authentication.getName());
        if (!worldId.equals(session.getRoom().getWorld().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        response.setContentType("application/x-ndjson");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        var writer = response.getWriter();
        java.util.function.Consumer<Map<String, ?>> emit = event -> {
            // A disconnected viewer must not cancel or retry an already paid turn.
            writer.println(JSON.writeValueAsString(event));
            writer.flush();
        };
        emit.accept(Map.of("type", "started"));
        String requestId=java.util.UUID.randomUUID().toString();
        var timing=new com.example.projectmm.application.dialogue.DialogueTiming("POCKET_SERVER");
        String[] candidate={""};
        try {
            var turn = com.example.projectmm.application.dialogue.SceneContinuityObservation.observe(() -> {
                var result = timing.measure(()->com.example.projectmm.application.dialogue.DialogueTokens.deliver(
                        reply -> emit.accept(Map.of("type", "delta", "reply", reply)),
                        () -> DialoguePreview.deliver(
                        reply -> {candidate[0]=reply;emit.accept(Map.of("type", "preview", "reply", reply));},
                        () -> dialogue.chat(sessionId, request.message()))));
                for(var finding:com.example.projectmm.application.dialogue.SceneContinuityObservation.current()) {
                    var evidence=com.example.projectmm.application.dialogue.ChatFailureStore.evidence(
                            new com.example.projectmm.application.dialogue.SceneContinuityService.ReviewRejectedException(
                                    finding.review(),finding.mode()),java.util.UUID.randomUUID().toString(),sessionId,"ADVISORY",candidate[0]);
                    var reasons=new java.util.ArrayList<>(evidence.reasons());
                    reasons.add(finding.type()+": "+finding.message());
                    evidence=new com.example.projectmm.application.dialogue.ChatFailureStore.Evidence(
                            evidence.requestId(),evidence.sessionId(),evidence.createdAt(),evidence.phase(),evidence.mode(),
                            evidence.approved(),java.util.List.copyOf(reasons),evidence.conflicts(),evidence.candidate(),evidence.truncated());
                    boolean saved=false;
                    try {failures.save(evidence);saved=true;}
                    catch(IOException|RuntimeException saveFailure) {LOG.warn("Continuity advisory save failed session={}",sessionId);}
                    if(administrator(authentication))
                        emit.accept(Map.of("type","review_warning","reviewFailure",evidence,"diagnosticsSaved",saved));
                }
                return result;
            });
            // The proxied service has returned: transaction and AFTER_COMMIT listeners finished.
            emit.accept(Map.of("type", "complete", "reply", turn.reply()));
        } catch (RuntimeException failure) {
            // Do not expose provider responses or claim rollback after an AFTER_COMMIT failure.
            var snapshot=timing.snapshot();
            var detail=com.example.projectmm.application.dialogue.DialogueFailure.describe(failure,snapshot);
            LOG.warn("Pocket chat failed requestId={} session={} detail={} timing={}",requestId,sessionId,detail,snapshot);
            var evidence=com.example.projectmm.application.dialogue.ChatFailureStore.evidence(failure,requestId,sessionId,detail.phase(),candidate[0]);
            boolean saved=false;
            if(evidence!=null) {
                try {failures.save(evidence);saved=true;}
                catch(IOException|RuntimeException persistenceFailure) {
                    LOG.warn("Pocket failure evidence save failed requestId={} type={}",requestId,persistenceFailure.getClass().getName());
                }
            }
            var event=new java.util.LinkedHashMap<String,Object>();
            event.put("type","failed");event.put("code",detail.code());event.put("phase",detail.phase());event.put("requestId",requestId);
            event.put("message",com.example.projectmm.application.dialogue.DialogueFailure.message(detail,requestId));
            if(evidence!=null && administrator(authentication)){event.put("reviewFailure",evidence);event.put("diagnosticsSaved",saved);}
            emit.accept(event);
        }
    }

    @GetMapping("/chat/failures")
    public java.util.List<com.example.projectmm.application.dialogue.ChatFailureStore.Evidence> failures(
            @PathVariable Long worldId,@PathVariable Long roomId,@PathVariable Long sessionId,
            Authentication authentication,HttpServletResponse response) throws IOException {
        if(!administrator(authentication))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"관리자만 검토 기록을 조회할 수 있어요.");
        var session=sessions.ownedSession(roomId,sessionId,authentication.getName());
        if(!worldId.equals(session.getRoom().getWorld().getId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        response.setHeader("Cache-Control","no-store");
        return failures.latest(sessionId);
    }
    private static boolean administrator(Authentication authentication) {
        return authentication!=null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a->java.util.Set.of("ROLE_ADMIN","ROLE_DEVELOPER").contains(a.getAuthority()));
    }

}
