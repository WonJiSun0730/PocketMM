package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import tools.jackson.databind.ObjectMapper;

public record DialogueEvaluationEvidence(String operation, String userInput, String modelInput, String originalReply, String effectiveModel,
                                         WorldRoomDialogueContext context) {
    public static String capture(String operation, String input, String modelInput, String reply, String effectiveModel, WorldRoomDialogueContext c) {
        var history = c.recentMessages();
        var limited = history == null ? java.util.List.<com.example.projectmm.domain.dialogue.DialogueMessageContext>of()
                : java.util.List.copyOf(history.subList(Math.max(0, history.size() - 6), history.size()));
        var snapshot = new WorldRoomDialogueContext(c.sessionId(), c.roomId(), c.roomTitle(), c.visibility(),
                c.simulatedAt(), c.world(), c.playerProfile(), c.introTitle(), c.introContent(), limited,
                c.roomCast(), c.activeCast(), c.sessionSystems(), c.playerState(), c.recalledMemories(),
                c.userNote(), c.pocketPlayGuide(), c.noControlPlayer(), c.aiProvider(), c.aiModel(), c.sceneFactsJson());
        return new ObjectMapper().writeValueAsString(new DialogueEvaluationEvidence(operation, input, modelInput, reply, effectiveModel, snapshot));
    }
}
