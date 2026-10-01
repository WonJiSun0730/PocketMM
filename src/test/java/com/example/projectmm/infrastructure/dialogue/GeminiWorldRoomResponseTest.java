package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.domain.dialogue.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiWorldRoomResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiDialogueGateway gateway =
            new GeminiDialogueGateway("test", "test", mapper);

    @Test
    void parsesRoomReplyCastAndValidSystemChanges() {
        var context = contextWithSystem(7L);

        String payload = """
                {
                  "reply":"Sherlock Holmes: Come in.",
                  "enterCharacterIds":[10],
                  "leaveCharacterIds":[20],
                  "systemChanges":[{"systemId":7,"delta":5}]
                }
                """;

        var turn = gateway.parseWorldRoomResponse(envelope(payload), context);

        assertThat(turn.reply()).isEqualTo("Sherlock Holmes: Come in.");
        assertThat(turn.castChange().enterCharacterIds()).containsExactly(10L);
        assertThat(turn.castChange().leaveCharacterIds()).containsExactly(20L);
        assertThat(turn.systemChanges())
                .containsExactly(new WorldRoomSystemChange(7L, 5));
    }

    @Test
    void rejectsWholeSystemChangeListWhenIdIsUnknownOrDuplicated() {
        var context = contextWithSystem(7L);

        String unknown = """
                {
                  "reply":"*The fog thickens.*",
                  "enterCharacterIds":[],
                  "leaveCharacterIds":[],
                  "systemChanges":[{"systemId":999,"delta":5}]
                }
                """;
        assertThat(gateway.parseWorldRoomResponse(envelope(unknown), context).systemChanges())
                .isEmpty();

        String duplicate = """
                {
                  "reply":"*The fog thickens.*",
                  "enterCharacterIds":[],
                  "leaveCharacterIds":[],
                  "systemChanges":[
                    {"systemId":7,"delta":5},
                    {"systemId":7,"delta":-2}
                  ]
                }
                """;
        assertThat(gateway.parseWorldRoomResponse(envelope(duplicate), context).systemChanges())
                .isEmpty();
    }

    private WorldRoomDialogueContext contextWithSystem(Long systemId) {
        var world = new DialogueWorldContext(
                1L,
                "Victorian London",
                "",
                List.of(),
                List.of()
        );
        var system = new WorldRoomSystemContext(
                systemId,
                "Case Tension",
                20,
                0,
                100,
                "Change when danger clearly escalates.",
                List.of()
        );
        return new WorldRoomDialogueContext(
                1L,
                "The Baker Street Case",
                com.example.projectmm.domain.chat.WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0),
                world,
                null,
                "Fog over Baker Street",
                "A dense fog hangs over Baker Street.",
                List.of(),
                List.of(),
                List.of(),
                List.of(system)
        );
    }

    private String envelope(String payload) {
        return mapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", payload))
                        )
                ))
        ));
    }
}
