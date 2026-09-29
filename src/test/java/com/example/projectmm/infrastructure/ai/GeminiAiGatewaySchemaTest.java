package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.domain.ai.AiContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAiGatewaySchemaTest {

    @Test
    void structured_output_schema에_결정값_범위가_포함된다() {

        GeminiAiGateway gateway =
                new GeminiAiGateway(
                        "test-key",
                        "test-model",
                        new ObjectMapper()
                );

        AiContext context =
                new AiContext(
                        1L,
                        "Millie",
                        80,
                        10,
                        LocalDateTime.of(
                                2026, 9, 21,
                                13, 0
                        ),
                        "MOOD_CHANGE",
                        "기분 변화가 발생했다.",
                        List.of(),
                        List.of()
                );

        Map<String, Object> request =
                gateway.createRequest(context);

        Map<String, Object> generationConfig =
                map(request.get("generationConfig"));

        Map<String, Object> schema =
                map(
                        generationConfig.get(
                                "responseJsonSchema"
                        )
                );

        Map<String, Object> properties =
                map(schema.get("properties"));

        assertRange(
                properties,
                "importance",
                0,
                10
        );

        assertRange(
                properties,
                "narrativeAnchor",
                0,
                10
        );

        assertRange(
                properties,
                "energyChange",
                -20,
                20
        );

        assertRange(
                properties,
                "moodChange",
                -20,
                20
        );

        assertRange(
                properties,
                "memoryAbilityChange",
                -20,
                20
        );

        assertRange(
                properties,
                "affinityChange",
                -10,
                10
        );

        assertRange(
                properties,
                "trustChange",
                -10,
                10
        );

        assertRange(
                properties,
                "tensionChange",
                -10,
                10
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(
            Object value
    ) {
        return (Map<String, Object>) value;
    }

    private void assertRange(
            Map<String, Object> properties,
            String property,
            int minimum,
            int maximum
    ) {
        Map<String, Object> definition =
                map(properties.get(property));

        assertThat(definition.get("type"))
                .isEqualTo("integer");

        assertThat(definition.get("minimum"))
                .isEqualTo(minimum);

        assertThat(definition.get("maximum"))
                .isEqualTo(maximum);
    }
}