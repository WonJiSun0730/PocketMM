package com.example.projectmm.infrastructure.memory;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class GeminiChatMemoryExtractorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiChatMemoryExtractor extractor = new GeminiChatMemoryExtractor("test", "test", mapper);

    @Test
    void parsesCompleteStructuredQuotesWithoutCallingGemini() {
        var result = extractor.parseResponse(response("STOP", "{\"memories\":[{\"quote\":\"나는 복숭아 알레르기가 있어.\",\"importance\":6}]}"));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().quote()).isEqualTo("나는 복숭아 알레르기가 있어.");
        assertThat(result.getFirst().importance()).isEqualTo(6);
        assertThat(extractor.parseResponse(response("STOP", "{\"memories\":[]}"))).isEmpty();
    }

    @Test
    void malformedBlockedAndTruncatedResultsAreFailuresNotEmptySuccesses() {
        for (String json : List.of("{}", "broken", "{\"memories\":[{\"quote\":5,\"importance\":6}]}")) {
            assertThatThrownBy(() -> extractor.parseResponse(response("STOP", json))).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> extractor.parseResponse(response("MAX_TOKENS", "{\"memories\":[]}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.parseResponse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String response(String reason, String text) {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", reason,
                "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }
}
