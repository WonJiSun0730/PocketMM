package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.ai.AiDecisionValidator;
import com.example.projectmm.domain.ai.AiContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiMemoryAccessDecisionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiAiGateway gateway = new GeminiAiGateway("unused", "unused", mapper);
    private final LocalDateTime at = LocalDateTime.of(2030, 1, 2, 12, 0);
    private final String legacyFields = "\"eventType\":\"X\",\"summary\":\"event\",\"importance\":5,"
            + "\"energyChange\":0,\"moodChange\":0,\"memoryAbilityChange\":0,"
            + "\"relationshipTargetCharacterId\":null,\"affinityChange\":0,\"trustChange\":0,\"tensionChange\":0";

    @Test
    void parsesEnvelopeAndTransfersAccessWithoutLosingExistingDeltas() {
        String body = """
                {"eventType":"UNRELATED_LABEL","summary":"event","importance":5,
                 "energyChange":-2,"moodChange":1,"memoryAbilityChange":-3,
                 "relationshipTargetCharacterId":2,"affinityChange":1,"trustChange":2,"tensionChange":0,
                 "memoryAccessChange":{"scope":"SINCE","accessLevel":"BLOCKED","since":"2030-01-01T00:00:00"}}
                """;
        String envelope = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                Map.of("parts", List.of(Map.of("text", body)))))));
        GeminiDecisionResponse response = ReflectionTestUtils.invokeMethod(gateway, "parseResponse", envelope);
        var decision = gateway.toDecision(response);
        assertThat(new AiDecisionValidator().validate(decision, at).valid()).isTrue();
        assertThat(decision.memoryAccessChange().scope()).isEqualTo("SINCE");
        assertThat(decision.memoryAccessChange().accessLevel()).isEqualTo("BLOCKED");
        assertThat(decision.stateDelta().memoryAbilityChange()).isEqualTo(-3);
        assertThat(decision.relationshipDelta().trustChange()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "80", "\"BLOCKED\"",
            "{\"scope\":\"ALL\",\"accessLevel\":\"BLOCKED\",\"characterId\":2}",
            "{\"scope\":\"ALL\",\"accessLevel\":\"BLOCKED\",\"memoryId\":9}",
            "{\"scope\":\"SINCE\",\"accessLevel\":\"BLOCKED\",\"since\":3}"})
    void rejectsMalformedCommandsAndInjectedTargets(String command) {
        var response = mapper.readValue("{" + legacyFields + ",\"memoryAccessChange\":"
                + command + "}", GeminiDecisionResponse.class);
        assertThat(new AiDecisionValidator().validate(gateway.toDecision(response), at).valid())
                .isEqualTo(command.equals("null"));
    }

    @Test
    void absentFieldPreservesLegacyResponseAndSchemaHasNoTargetIds() {
        var response = mapper.readValue("{" + legacyFields + "}", GeminiDecisionResponse.class);
        assertThat(gateway.toDecision(response).memoryAccessChange()).isNull();
        Object request = ReflectionTestUtils.invokeMethod(gateway, "createRequest",
                new AiContext(1L, "NPC", 70, 70, at, "X", "event", List.of(), List.of()));
        var json = mapper.readTree(mapper.writeValueAsString(request));
        var access = json.path("generationConfig").path("responseJsonSchema").path("properties").path("memoryAccessChange");
        assertThat(access.path("additionalProperties").asBoolean()).isFalse();
        assertThat(access.path("properties").size()).isEqualTo(3);
        assertThat(access.toString()).doesNotContain("characterId", "memoryId");
    }

    @Test
    void narrativeAnchor가_Gemini응답에서_AiDecision으로_전달된다() {

        String body = """
            {
              "eventType":"RELATIONSHIP_MILESTONE",
              "summary":"관계를 정의하는 중요한 약속을 나눴다.",
              "importance":7,
              "narrativeAnchor":9,
              "energyChange":0,
              "moodChange":5,
              "memoryAbilityChange":0,
              "relationshipTargetCharacterId":null,
              "affinityChange":0,
              "trustChange":0,
              "tensionChange":0,
              "memoryAccessChange":null
            }
            """;

        String envelope =
                mapper.writeValueAsString(
                        Map.of(
                                "candidates",
                                List.of(
                                        Map.of(
                                                "content",
                                                Map.of(
                                                        "parts",
                                                        List.of(
                                                                Map.of(
                                                                        "text",
                                                                        body
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                );

        GeminiDecisionResponse response =
                ReflectionTestUtils.invokeMethod(
                        gateway,
                        "parseResponse",
                        envelope
                );

        var decision =
                gateway.toDecision(response);

        assertThat(response.resolvedNarrativeAnchor())
                .isEqualTo(9);

        assertThat(decision.narrativeAnchor())
                .isEqualTo(9);

        assertThat(decision.importance())
                .isEqualTo(7);
    }
}
