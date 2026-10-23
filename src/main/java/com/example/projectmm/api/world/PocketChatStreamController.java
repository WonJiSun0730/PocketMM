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
        try {
            var turn = timing.measure(()->com.example.projectmm.application.dialogue.DialogueTokens.deliver(
                    reply -> emit.accept(Map.of("type", "delta", "reply", reply)),
                    () -> DialoguePreview.deliver(
                    reply -> emit.accept(Map.of("type", "preview", "reply", reply)),
                    () -> dialogue.chat(sessionId, request.message()))));
            // The proxied service has returned: transaction and AFTER_COMMIT listeners finished.
            emit.accept(Map.of("type", "complete", "reply", turn.reply()));
        } catch (RuntimeException failure) {
            // Do not expose provider responses or claim rollback after an AFTER_COMMIT failure.
            var snapshot=timing.snapshot();
            var detail=com.example.projectmm.application.dialogue.DialogueFailure.describe(failure,snapshot);
            LOG.warn("Pocket chat failed requestId={} session={} detail={} timing={}",requestId,sessionId,detail,snapshot);
            emit.accept(Map.of("type", "failed", "code",detail.code(),"phase",detail.phase(),"requestId",requestId,
                    "message",com.example.projectmm.application.dialogue.DialogueFailure.message(detail,requestId)));
        }
    }
}
