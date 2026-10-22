package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.dialogue.DialogueTurn;
import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class PocketDialogueGatewayRouter implements DialogueGateway {

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.example.projectmm.domain.chat.WorldRoomSessionRepository sessions;
    private final GeminiDialogueGateway gemini;
    private final OpenAiDialogueGateway openAi;

    @Override
    public String reviewWorldRoomContinuity(WorldRoomDialogueContext context, String evidenceJson) {
        var selected = resolveSelection(context);
        return AiUsageTracker.withPurpose(selected.sessionId(), "SCENE_CONTINUITY",
                () -> "OPENAI".equalsIgnoreCase(selected.aiProvider())
                        ? openAi.reviewWorldRoomContinuity(selected,evidenceJson)
                        : gemini.reviewWorldRoomContinuity(selected,evidenceJson));
    }

    @Override
    public String initializeWorldRoomScene(WorldRoomDialogueContext context, boolean timeUnset, String opening) {
        return AiUsageTracker.withPurpose(context.sessionId(),"INITIAL_SCENE",
                () -> DialogueGateway.super.initializeWorldRoomScene(context,timeUnset,opening));
    }

    @Override
    public String setupWorldRoomScene(WorldRoomDialogueContext context, String input) {
        return AiUsageTracker.withPurpose(context.sessionId(),"SCENE_SETUP",
                () -> DialogueGateway.super.setupWorldRoomScene(context,input));
    }

    @Override
    public String generate(DialogueContext context, String userMessage) {
        requireLegacyProvider();
        return gemini.generate(context, userMessage);
    }

    @Override
    public DialogueTurn generateTurn(DialogueContext context, String userMessage) {
        requireLegacyProvider();
        return gemini.generateTurn(context, userMessage);
    }

    private void requireLegacyProvider() {
        Long id=AiUsageTracker.currentSessionId();
        if(sessions!=null && id!=null && sessions.findById(id).map(s->"OPENAI".equalsIgnoreCase(s.getAiProvider())).orElse(true))
            throw new IllegalStateException("Legacy dialogue cannot serve an OpenAI Pocket; Gemini call blocked");
    }

    private WorldRoomDialogueContext resolveSelection(WorldRoomDialogueContext context) {
        if (sessions != null && context.sessionId() != null) {
            var session=sessions.findById(context.sessionId()).orElseThrow();
            context=context.withAiSelection(session.getAiProvider(),session.getAiModel());
        }
        return context;
    }

    @Override
    public WorldRoomTurn generateWorldRoomTurn(WorldRoomDialogueContext context, String userMessage) {
        context = resolveSelection(context);
        String provider = context.aiProvider() == null ? "GEMINI" : context.aiProvider();
        return "OPENAI".equalsIgnoreCase(provider)
                ? openAi.generateWorldRoomTurn(context, userMessage)
                : gemini.generateWorldRoomTurn(context, userMessage);
    }
}
