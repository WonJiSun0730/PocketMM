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
        var selected = resolveSelection(context);
        return AiUsageTracker.withPurpose(context.sessionId(),"INITIAL_SCENE",
                () -> "OPENAI".equalsIgnoreCase(selected.aiProvider())
                        ? openAi.initializeWorldRoomScene(selected.withAiSelection("OPENAI","gpt-5.6-luna"),timeUnset,opening)
                        : DialogueGateway.super.initializeWorldRoomScene(selected,timeUnset,opening));
    }

    @Override
    public String setupWorldRoomScene(WorldRoomDialogueContext context, String input) {
        var selected = resolveSelection(context);
        return AiUsageTracker.withPurpose(context.sessionId(),"SCENE_SETUP",
                () -> "OPENAI".equalsIgnoreCase(selected.aiProvider())
                        ? openAi.setupWorldRoomScene(selected.withAiSelection("OPENAI","gpt-5.6-luna"),input)
                        : DialogueGateway.super.setupWorldRoomScene(selected,input));
    }

    @Override
    public WorldRoomTurn analyzeEditedWorldRoomState(WorldRoomDialogueContext context, String reply) {
        var selected = resolveSelection(context);
        return AiUsageTracker.withPurpose(context.sessionId(),"STATE_EXTRACTION",
                () -> "OPENAI".equalsIgnoreCase(selected.aiProvider())
                        ? openAi.analyzeEditedWorldRoomState(selected.withAiSelection("OPENAI","gpt-5.6-luna"),reply)
                        : DialogueGateway.super.analyzeEditedWorldRoomState(selected,reply));
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
