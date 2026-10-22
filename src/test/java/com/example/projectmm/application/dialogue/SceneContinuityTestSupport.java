package com.example.projectmm.application.dialogue;

import tools.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Existing scenarios isolate their original concern; continuity has its own adversarial tests. */
public final class SceneContinuityTestSupport {
    private SceneContinuityTestSupport() {}
    public static void approveUnchanged(DialogueGateway gateway) {
        var mapper = new ObjectMapper();
        when(gateway.reviewWorldRoomContinuity(any(), anyString())).thenAnswer(call -> {
            var request = mapper.readTree((String) call.getArgument(1));
            var reply = mapper.createObjectNode();
            reply.put("approved",true);
            reply.set("turn", request.path("candidate"));
            reply.putArray("changes");
            reply.putArray("conflicts");
            return mapper.writeValueAsString(reply);
        });
    }
}
