package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.chat.ChatStateUpdate;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.dialogue.DialogueContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class GeminiDialogueResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiDialogueGateway gateway = new GeminiDialogueGateway("test", "test", mapper);

    @Test void extractsOnlyRpBodyAndIgnoresThoughtParts() {
        String json = """
                {"reply":"*무거운 상자를 옮겨 지쳤다.*\\n잠시 쉬죠.","state":{
                "energyDelta":-6,"moodDelta":0,"energyEvidence":"무거운 상자를 옮겨 지쳤다","moodEvidence":""}}
                """;
        String response = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                "content", Map.of("parts", List.of(Map.of("thought", true, "text", "비공개 추론"),
                        Map.of("text", json.substring(0, 30)), Map.of("text", json.substring(30))))))));
        var turn = gateway.parseResponse(response);
        assertThat(turn.reply()).isEqualTo("*무거운 상자를 옮겨 지쳤다.*\n잠시 쉬죠.");
        assertThat(turn.reply()).doesNotContain("energyDelta", "비공개 추론");
        assertThat(ChatStateUpdate.resolve(70, 70, turn.reply(), turn.stateChange()).energy()).isEqualTo(64);
    }

    @Test void badMetadataDoesNotDiscardValidDialogueOrCoerceTypes() {
        for (String state : List.of("null", "{}",
                "{\"energyDelta\":\"5\",\"moodDelta\":0,\"energyEvidence\":\"쉬었다\",\"moodEvidence\":\"\"}",
                "{\"energyDelta\":5.5,\"moodDelta\":0,\"energyEvidence\":\"쉬었다\",\"moodEvidence\":\"\"}",
                "{\"energyDelta\":9999999999999999,\"moodDelta\":0,\"energyEvidence\":\"쉬었다\",\"moodEvidence\":\"\"}")) {
            var turn = gateway.parseResponse(envelope("{\"reply\":\"*쉬었다.*\",\"state\":" + state + "}", "STOP"));
            assertThat(turn.reply()).isEqualTo("*쉬었다.*");
            assertThat(ChatStateUpdate.resolve(70, 70, turn.reply(), turn.stateChange()).status()).isEqualTo("REJECTED");
        }
    }

    @Test void refusesTruncatedBlockedOrInvalidBodiesInsteadOfLeakingJson() {
        for (String body : List.of(
                "{",
                "{}",
                "{\"reply\":42}",
                "{\"reply\":\"  \"}",
                "*응답*\n\"energyDelta\": 10",
                "*응답*\n\"playerState\": {\"energyDelta\": -3}",
                "```json\n{\"reply\":",
                "[잘린 배열"
        ))
            assertThatThrownBy(() -> gateway.parseResponse(envelope(body, "STOP"))).isInstanceOf(IllegalStateException.class);
        for (String finish : List.of("MAX_TOKENS", "SAFETY"))
            assertThatThrownBy(() -> gateway.parseResponse(envelope("{\"reply\":\"안녕\"}", finish))).isInstanceOf(IllegalStateException.class);
    }

    @Test void requestAsksForSingleJsonResponseWithPrivateThoughtAndRepeatedComplaintBoundaries() {
        var context = new DialogueContext(
                1L, "마르바스", null,
                70, 70,
                LocalDateTime.of(2026, 9, 23, 12, 0),
                ContentMode.NORMAL,
                List.of(), List.of(), List.of(),
                null,
                70, 70,
                List.of()
        );
        Object request = ReflectionTestUtils.invokeMethod(gateway, "createRequest", context, "안녕하세요");
        String json = mapper.writeValueAsString(request);
        assertThat(json).contains("APPLICATION_JSON", "energyDelta", "moodEvidence", "habitual complaints",
                "Private user thoughts cannot affect either state", "Do not invent", "OOC commands");
        var format = mapper.readTree(json).path("generationConfig").path("responseFormat").path("text");
        // Google v1beta discovery의 TextResponseFormat.mimeType enum과 일치해야 한다.
        // 기존 responseMimeType의 MIME 문자열을 새 필드에 쓰면 실제 API가 400을 반환한다.
        assertThat(format.path("mimeType").asText()).isEqualTo("APPLICATION_JSON");
        assertThat(json).doesNotContain("application/json");
        assertThat(format.path("schema").path("required").toString()).contains("reply", "state");
        assertThat(format.path("schema").path("properties").path("state").path("properties")
                .path("energyDelta").path("minimum").asInt()).isEqualTo(-10);
    }

    @Test
    void parsesRelationshipChangeFromDialogueResponse() {

        String body = """
            {
              "reply":"*잠시 바라보다가 부드럽게 웃었다.*",
              "state":{
                "energyDelta":0,
                "moodDelta":0,
                "energyEvidence":"",
                "moodEvidence":""
              },
              "playerState":{
                "energyDelta":0,
                "moodDelta":0,
                "energyEvidence":"",
                "moodEvidence":""
              },
              "customStateChanges":[],
              "relationshipChange":{
                "targetCharacterId":2,
                "affinityDelta":3,
                "trustDelta":2,
                "tensionDelta":-1
              }
            }
            """;

        var turn =
                gateway.parseResponse(
                        envelope(body, "STOP")
                );

        assertThat(turn.relationshipChange().targetCharacterId())
                .isEqualTo(2L);

        assertThat(turn.relationshipChange().delta().affinityChange())
                .isEqualTo(3);

        assertThat(turn.relationshipChange().delta().trustChange())
                .isEqualTo(2);

        assertThat(turn.relationshipChange().delta().tensionChange())
                .isEqualTo(-1);
    }

    @Test
    void parsesNoRelationshipChangeAsZeroDelta() {

        String body = """
            {
              "reply":"*고개를 끄덕였다.*",
              "state":{
                "energyDelta":0,
                "moodDelta":0,
                "energyEvidence":"",
                "moodEvidence":""
              },
              "playerState":{
                "energyDelta":0,
                "moodDelta":0,
                "energyEvidence":"",
                "moodEvidence":""
              },
              "customStateChanges":[],
              "relationshipChange":{
                "targetCharacterId":null,
                "affinityDelta":0,
                "trustDelta":0,
                "tensionDelta":0
              }
            }
            """;

        var turn =
                gateway.parseResponse(
                        envelope(body, "STOP")
                );

        assertThat(turn.relationshipChange().targetCharacterId())
                .isNull();

        assertThat(turn.relationshipChange().hasChange())
                .isFalse();
    }

    @Test
    void malformedRelationshipChangeIsRejectedWithoutDiscardingReply() {

        String body = """
            {
              "reply":"*조용히 웃었다.*",
              "state":{
                "energyDelta":0,
                "moodDelta":0,
                "energyEvidence":"",
                "moodEvidence":""
              },
              "playerState":{
                "energyDelta":0,
                "moodDelta":0,
                "energyEvidence":"",
                "moodEvidence":""
              },
              "customStateChanges":[],
              "relationshipChange":{
                "targetCharacterId":"wrong",
                "affinityDelta":"five",
                "trustDelta":0,
                "tensionDelta":0
              }
            }
            """;

        var turn =
                gateway.parseResponse(
                        envelope(body, "STOP")
                );

        assertThat(turn.reply())
                .isEqualTo("*조용히 웃었다.*");

        assertThat(turn.relationshipChange().delta())
                .isNull();

        assertThat(turn.relationshipChange().hasChange())
                .isFalse();
    }

    @Test void plainRpResponseSurvivesWithoutGuessingStateOrExposingMetadata() {
        for (String body : List.of("*서류를 내려놓았다.*\n안녕하세요.", "안녕하세요.", "\"안녕하세요.\"\n*고개를 끄덕였다.*")) {
            var turn = gateway.parseResponse(envelope(body, "STOP"));
            assertThat(turn.reply()).isEqualTo(body);
            var update = ChatStateUpdate.resolve(70, 60, turn.reply(), turn.stateChange());
            assertThat(update.status()).isEqualTo("REJECTED");
            assertThat(update.energy()).isEqualTo(70);
            assertThat(update.mood()).isEqualTo(60);
        }
    }

    @Test void acceptsJsonStringAndFencedObjectWithoutShowingTheirWrappers() {
        assertThat(gateway.parseResponse(envelope("\"*고개를 끄덕였다.*\"", "STOP")).reply()).isEqualTo("*고개를 끄덕였다.*");
        assertThat(gateway.parseResponse(envelope("```json\n{\"reply\":\"안녕하세요\",\"state\":null}\n```", "STOP")).reply())
                .isEqualTo("안녕하세요");
    }

    private String envelope(String body, String finish) {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", finish,
                "content", Map.of("parts", List.of(Map.of("text", body)))))));
    }
}
