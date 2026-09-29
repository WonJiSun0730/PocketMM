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
        var result = extractor.parseResponse(response("STOP", """
                            {"memories":[{
                              "quote":"나는 복숭아 알레르기가 있어.",
                              "importance":6,
                              "fact":null,
                              "details":[]
                            }]}
                            """));
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

    @Test void parsesGroundedStructureAndRejectsInventedOrMalformedFields() {
        String valid = """
            {"quote":"내 반려묘 이름은 감자야.","importance":5,"fact":
            {"kind":"RELATIONSHIP","subject":"반려묘","relation":"이름","value":"감자"},
            "details":[]}
            """;
        var fact = extractor.parseResponse(response("STOP", "{\"memories\":[" + valid + "]}"))
                .getFirst().fact();
        assertThat(fact.getSubject()).isEqualTo("반려묘");
        assertThat(fact.getValue()).isEqualTo("감자");
        for (String invalid : List.of(valid.replace("\"value\":\"감자\"", "\"value\":\"Potato\""),
                valid.replace("RELATIONSHIP", "INFERRED_FACT"),
                valid.replace("\"subject\":\"반려묘\"", "\"subject\":null"),
                valid.replace("\"relation\":\"이름\"", "\"relation\":42"))) {
            var result = extractor.parseResponse(response("STOP", "{\"memories\":[" + invalid + "," + valid + "]}"));
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().fact().getValue()).isEqualTo("감자");
        }
        assertThatThrownBy(() -> extractor.parseResponse(response("STOP",
                valid.replace("\"importance\":5", "\"importance\":4294967301").transform(s -> "{\"memories\":[" + s + "]}"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void requestDefinesStructureWithoutRelaxingSpeechEvidencePolicy() {
        var request = mapper.valueToTree(extractor.createRequest("내 반려묘 이름은 감자야."));
        var fields = request.path("generationConfig").path("responseSchema").path("properties")
                .path("memories").path("items").path("properties");
        assertThat(fields.has("fact")).isTrue();
        assertThat(fields.path("fact").path("nullable").asBoolean()).isTrue();
        assertThat(request.path("systemInstruction").toString()).contains("VERBATIM", "PLAN", "USER TESTIMONY",
                "private", "OOC", "exact substring", "Do not infer");
        var detailFields =
                fields.path("details")
                        .path("items")
                        .path("properties");

        assertThat(detailFields.has("text"))
                .isTrue();

        assertThat(detailFields.has("salience"))
                .isTrue();

        assertThat(detailFields.has("protectedAnchor"))
                .isTrue();

        assertThat(
                request.path("systemInstruction")
                        .toString()
        ).contains(
                "exact nonempty substring",
                "LOW",
                "NORMAL",
                "HIGH",
                "CRITICAL",
                "protectedAnchor"
        );
    }

    @Test
    void parsesGroundedMemoryDetails() {

        String json = """
            {
              "memories": [{
                "quote": "내 반려묘 이름은 감자야.",
                "importance": 7,
                "fact": null,
                "details": [
                  {
                    "text": "감자",
                    "salience": "HIGH",
                    "protectedAnchor": false
                  },
                  {
                    "text": "반려묘",
                    "salience": "NORMAL",
                    "protectedAnchor": true
                  }
                ]
              }]
            }
            """;

        var memory =
                extractor.parseResponse(
                        response("STOP", json)
                ).getFirst();

        assertThat(memory.details())
                .hasSize(2);

        assertThat(
                memory.details()
                        .get(0)
                        .text()
        ).isEqualTo("감자");

        assertThat(
                memory.details()
                        .get(0)
                        .salience()
        ).isEqualTo(
                com.example.projectmm.domain.memory
                        .MemoryDetailSalience.HIGH
        );

        assertThat(
                memory.details()
                        .get(1)
                        .protectedAnchor()
        ).isTrue();
    }

    @Test
    void rejectsUngroundedOrMalformedMemoryDetails() {

        String json = """
            {
              "memories": [{
                "quote": "내 반려묘 이름은 감자야.",
                "importance": 7,
                "fact": null,
                "details": [
                  {
                    "text": "감자",
                    "salience": "HIGH",
                    "protectedAnchor": false
                  },
                  {
                    "text": "고구마",
                    "salience": "CRITICAL",
                    "protectedAnchor": true
                  },
                  {
                    "text": "반려묘",
                    "salience": "INVALID",
                    "protectedAnchor": true
                  }
                ]
              }]
            }
            """;

        var details =
                extractor.parseResponse(
                        response("STOP", json)
                ).getFirst().details();

        assertThat(details)
                .hasSize(1);

        assertThat(
                details.getFirst().text()
        ).isEqualTo("감자");
    }
}
