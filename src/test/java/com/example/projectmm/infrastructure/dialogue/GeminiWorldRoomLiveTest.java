package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldConversation;
import com.example.projectmm.domain.dialogue.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"local", "gemini"})
@Tag("live")
class GeminiWorldRoomLiveTest {

    @Autowired
    DialogueGateway dialogueGateway;

    @Test
    void actualGeminiReturnsParsableWorldRoomTurn() {
        var sherlock = new WorldRoomCharacterContext(
                10L,
                "Sherlock Holmes",
                null,
                70,
                70,
                false
        );

        var context = new WorldRoomDialogueContext(
                1L,
                "The Baker Street Case",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0),
                new DialogueWorldContext(
                        1L,
                        "Victorian London",
                        "Keep the mystery grounded in Victorian London.",
                        List.of(),
                        List.of()
                ),
                new DialoguePlayerProfile(
                        "Eleanor Watson",
                        "A visitor arriving at Baker Street with a sealed letter."
                ),
                "Fog over Baker Street",
                "A dense fog hangs over Baker Street. Eleanor stands outside 221B with a sealed letter.",
                List.of(),
                List.of(sherlock),
                List.of(),
                List.of()
        );

        var turn = dialogueGateway.generateWorldRoomTurn(
                context,
                "문을 두 번 두드리고 안쪽의 기척을 기다린다."
        );

        assertThat(turn).isNotNull();
        assertThat(turn.reply()).isNotBlank();
        assertThat(turn.castChange()).isNotNull();
        assertThat(turn.systemChanges()).isNotNull();

        System.out.println();
        System.out.println("==============================");
        System.out.println("WORLD ROOM LIVE RESPONSE");
        System.out.println("------------------------------");
        System.out.println(turn.reply());
        System.out.println("------------------------------");
        System.out.println("ENTER: " + turn.castChange().enterCharacterIds());
        System.out.println("LEAVE: " + turn.castChange().leaveCharacterIds());
        System.out.println("SYSTEM CHANGES: " + turn.systemChanges());
        System.out.println("==============================");
    }
}
