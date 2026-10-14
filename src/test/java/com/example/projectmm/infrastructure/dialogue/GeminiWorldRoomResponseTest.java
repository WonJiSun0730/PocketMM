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
                  "systemChanges":[{"systemId":7,"delta":5}],
                  "elapsedSeconds":95
                }
                """;

        var turn = gateway.parseWorldRoomResponse(envelope(payload), context);

        assertThat(turn.reply()).isEqualTo("Sherlock Holmes: Come in.");
        assertThat(turn.castChange().enterCharacterIds()).containsExactly(10L);
        assertThat(turn.castChange().leaveCharacterIds()).containsExactly(20L);
        assertThat(turn.systemChanges())
                .containsExactly(new WorldRoomSystemChange(7L, 5));
        assertThat(turn.elapsedSeconds()).isEqualTo(95);
    }


    @Test
    void discardsInvalidNarrativeElapsedTime() {
        var context = contextWithSystem(7L);

        String payload = """
                {
                  "reply":"*A moment hangs in the air.*",
                  "enterCharacterIds":[],
                  "leaveCharacterIds":[],
                  "systemChanges":[],
                  "elapsedSeconds":-30
                }
                """;

        assertThat(gateway.parseWorldRoomResponse(envelope(payload), context).elapsedSeconds())
                .isZero();
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

    @Test void capturesGeminiReportedUsageForTheExactPocketCall() {
        var builder=org.springframework.web.client.RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        var server=org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var tested=new GeminiDialogueGateway("test-key","test-model",mapper,builder.build());
        var c=contextWithSystem(7L);
        var context=new WorldRoomDialogueContext(9L,c.roomId(),c.roomTitle(),c.visibility(),c.simulatedAt(),c.world(),c.playerProfile(),
                c.introTitle(),c.introContent(),c.recentMessages(),c.roomCast(),c.activeCast(),c.sessionSystems(),c.playerState(),c.recalledMemories(),
                c.userNote(),c.pocketPlayGuide(),c.noControlPlayer(),c.aiProvider(),c.aiModel());
        var response=mapper.readTree(envelope("{\"reply\":\"A quiet day.\",\"enterCharacterIds\":[],\"leaveCharacterIds\":[],\"systemChanges\":[],\"elapsedSeconds\":0}"));
        var body=response.toString();
        body=body.substring(0,body.length()-1)+",\"usageMetadata\":{\"promptTokenCount\":100,\"cachedContentTokenCount\":20,\"candidatesTokenCount\":40,\"thoughtsTokenCount\":10,\"totalTokenCount\":150}}";
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(body,org.springframework.http.MediaType.APPLICATION_JSON));
        var captured=AiUsageTracker.capture(9L,()->tested.generateWorldRoomTurn(context,"Retell"));
        assertThat(captured.usage().model()).isEqualTo("test-model");
        assertThat(captured.usage().inputTokens()).isEqualTo(100);
        assertThat(captured.usage().outputTokens()).isEqualTo(50);
        assertThat(captured.usage().totalTokens()).isEqualTo(150);
        server.verify();
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
                        "finishReason", "STOP",
                        "content", Map.of(
                                "parts", List.of(Map.of("text", payload))
                        )
                ))
        ));
    }
}
