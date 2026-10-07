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

    private final GeminiDialogueGateway gemini;
    private final OpenAiDialogueGateway openAi;

    @Override
    public String generate(DialogueContext context, String userMessage) {
        return gemini.generate(context, userMessage);
    }

    @Override
    public DialogueTurn generateTurn(DialogueContext context, String userMessage) {
        return gemini.generateTurn(context, userMessage);
    }

    @Override
    public WorldRoomTurn generateWorldRoomTurn(WorldRoomDialogueContext context, String userMessage) {
        String provider = context.aiProvider() == null ? "GEMINI" : context.aiProvider();
        return "OPENAI".equalsIgnoreCase(provider)
                ? openAi.generateWorldRoomTurn(context, userMessage)
                : gemini.generateWorldRoomTurn(context, userMessage);
    }
}
