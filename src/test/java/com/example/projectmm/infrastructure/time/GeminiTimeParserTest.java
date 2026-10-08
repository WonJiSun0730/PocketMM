package com.example.projectmm.infrastructure.time;

import com.example.projectmm.domain.time.AiTimeParseResult;
import com.example.projectmm.domain.time.AiTimeParseResult.DurationScale;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiTimeParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiTimeParser parser = new GeminiTimeParser("test-key", "test-model", mapper);

    @Test
    void readsExplicitSceneClockAndRejectsMalformedTarget() {
        for (String target : List.of("2026-10-08T15:25:00", "invalid")) {
            String json = "{\"matched\":true,\"type\":\"SCENE_TIME\",\"precision\":\"EXACT\","
                    + "\"confidence\":0.95,\"sourceExpression\":\"3시 25분이다\",\"targetTime\":\"" + target + "\"}";
            String response = mapper.writeValueAsString(Map.of("candidates", List.of(
                    Map.of("content", Map.of("parts", List.of(Map.of("text", json)))))));
            AiTimeParseResult result = ReflectionTestUtils.invokeMethod(parser, "parseResponse", response);
            assertThat(result.type()).isEqualTo(AiTimeParseResult.TimeType.SCENE_TIME);
            if (target.equals("invalid")) assertThat(result.targetTime()).isNull();
            else assertThat(result.targetTime()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 8, 15, 25));
        }
    }

    @Test
    void readsScaleWithoutNumericDuration() {
        var result = parse("\"durationScale\":\"LONG\",");
        assertThat(result.matched()).isTrue();
        assertThat(result.durationScale()).isEqualTo(DurationScale.LONG);
        assertThat(result.amount()).isNull();
        assertThat(result.unit()).isNull();
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.sourceExpression()).isEqualTo("한참 시간이 흐른 뒤");
    }

    @Test
    void missingInvalidOrNullScaleFallsBackToUnspecified() {
        assertThat(parse("").durationScale()).isEqualTo(DurationScale.UNSPECIFIED);
        assertThat(parse("\"durationScale\":\"INVALID\",").durationScale()).isEqualTo(DurationScale.UNSPECIFIED);
        assertThat(parse("\"durationScale\":null,").durationScale()).isEqualTo(DurationScale.UNSPECIFIED);
    }

    private AiTimeParseResult parse(String scaleField) {
        String json = "{\"matched\":true,\"type\":\"RELATIVE\","
                + "\"precision\":\"AMBIGUOUS\",\"amount\":null,\"unit\":null,"
                + scaleField + "\"confidence\":0.95,\"sourceExpression\":\"한참 시간이 흐른 뒤\"}";
        String response = mapper.writeValueAsString(Map.of("candidates", List.of(
                Map.of("content", Map.of("parts", List.of(Map.of("text", json)))))));
        return ReflectionTestUtils.invokeMethod(parser, "parseResponse", response);
    }
}
