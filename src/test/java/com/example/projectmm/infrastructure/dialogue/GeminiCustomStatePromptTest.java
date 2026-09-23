package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.dialogue.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiCustomStatePromptTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiDialogueGateway gateway = new GeminiDialogueGateway("test", "test", mapper);

    @Test
    void requestIncludesConfirmedValuesAndAuthorityRulesWhileKeepingCoreSchema() {
        var request = request(List.of(
                new DialogueCustomState("피에타", 80, 0, 100, "%", "특수 에너지 자원"),
                new DialogueCustomState("마력", -5.5, -10, 200, "MP", "마법 자원")));
        String prompt = request.path("systemInstruction").path("parts").path(0).path("text").asText();
        assertThat(prompt).contains("[CURRENT CUSTOM STATE — SERVER AUTHORITATIVE]",
                "피에타: 80.0 % (range 0.0..100.0)", "특수 에너지 자원",
                "마력: -5.5 MP (range -10.0..200.0)", "마법 자원",
                "current confirmed", "If prior dialogue conflicts", "current server values take precedence",
                "Do not modify these values or invent changes", "including in narration",
                "state JSON is ONLY for the controlled NPC's core energy/mood",
                "Do not put custom states in state or propose custom state changes");
        assertThat(prompt).doesNotContain("definitionId", "currentValue", "defaultValue");
        assertThat(request.path("contents").path(0).path("parts").path(0).path("text").asText())
                .isEqualTo("피에타는 67이다.");
        var properties = request.path("generationConfig").path("responseFormat").path("text")
                .path("schema").path("properties");
        assertThat(properties.size()).isEqualTo(2);
        assertThat(properties.has("reply")).isTrue();
        var state = properties.path("state").path("properties");
        assertThat(state.size()).isEqualTo(4);
        for (String field : List.of("energyDelta", "moodDelta", "energyEvidence", "moodEvidence")) {
            assertThat(state.has(field)).isTrue();
        }
    }

    @Test
    void omitsSectionWhenNoCustomValuesExist() {
        String prompt = request(List.of()).path("systemInstruction").path("parts").path(0).path("text").asText();
        assertThat(prompt).doesNotContain("[CURRENT CUSTOM STATE");
        assertThat(prompt).contains("energyDelta", "moodDelta");
    }

    @Test
    void optionalUnitAndDescriptionDoNotProduceNullText() {
        String prompt = request(List.of(new DialogueCustomState("중독도", 3, 0, 10, null, null)))
                .path("systemInstruction").path("parts").path(0).path("text").asText();
        assertThat(prompt).contains("중독도: 3.0 (range 0.0..10.0)");
        assertThat(prompt.substring(prompt.indexOf("[CURRENT CUSTOM STATE"))).doesNotContain("null");
    }

    private JsonNode request(List<DialogueCustomState> states) {
        var context = new DialogueContext(1L, "NPC", null, 70, 70, LocalDateTime.of(2030, 1, 1, 12, 0),
                ContentMode.NORMAL, List.of(new DialogueMessageContext(ChatRole.CHARACTER, "피에타는 67이다.",
                LocalDateTime.of(2030, 1, 1, 11, 0))), List.of(), List.of(), null, states);
        Object request = ReflectionTestUtils.invokeMethod(gateway, "createRequest", context, "현재 상태는?");
        return mapper.readTree(mapper.writeValueAsString(request));
    }
}
