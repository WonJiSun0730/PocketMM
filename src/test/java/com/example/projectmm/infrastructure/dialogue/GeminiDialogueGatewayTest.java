package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.DialogueContextBuilder;
import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.dialogue.DialogueContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"local", "gemini"})
//@Tag("live")
class GeminiDialogueGatewayTest {

    @Autowired
    private DialogueContextBuilder dialogueContextBuilder;

    @Autowired
    private DialogueGateway dialogueGateway;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void 실제_Gemini와_대화한다() {

        Long characterId = 1L;

        Conversation conversation =
                conversationRepository
                        .findTopByCharacterIdOrderByCreatedAtDesc(characterId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Test conversation not found. characterId="
                                                + characterId
                                )
                        );

        DialogueContext context =
                dialogueContextBuilder.build(
                        characterId,
                        conversation.getId(),
                        ContentMode.NORMAL
                );

        String response =
                dialogueGateway.generate(
                        context,
                        "오늘 뭐 했어?"
                );

        System.out.println();
        System.out.println("==============================");
        System.out.println("CHARACTER: " + context.characterName());
        System.out.println("ENERGY: " + context.energy());
        System.out.println("MOOD: " + context.mood());
        System.out.println("MEMORIES: " + context.memories().size());
        System.out.println("RELATIONSHIPS: " + context.relationships().size());
        System.out.println("------------------------------");
        System.out.println(response);
        System.out.println("==============================");
    }
}