package com.example.projectmm.infrastructure.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiDecisionParserTest {

    private GeminiDecisionParser parser;

    @BeforeEach
    void setUp() {
        parser =
                new GeminiDecisionParser(
                        new ObjectMapper()
                );
    }

    @Test
    void null_응답을_EMPTY_RESPONSE로_거부한다() {

        assertThatThrownBy(
                () -> parser.parse(null)
        )
                .isInstanceOfSatisfying(
                        GeminiDecisionParseException.class,
                        exception ->
                                assertThat(exception.reason())
                                        .isEqualTo(
                                                GeminiDecisionParseException.Reason.EMPTY_RESPONSE
                                        )
                );
    }

    @Test
    void 빈_응답을_EMPTY_RESPONSE로_거부한다() {

        assertThatThrownBy(
                () -> parser.parse("   ")
        )
                .isInstanceOfSatisfying(
                        GeminiDecisionParseException.class,
                        exception ->
                                assertThat(exception.reason())
                                        .isEqualTo(
                                                GeminiDecisionParseException.Reason.EMPTY_RESPONSE
                                        )
                );
    }

    @Test
    void content가_없는_응답을_MISSING_CONTENT로_거부한다() {

        String response = """
                {
                  "candidates": []
                }
                """;

        assertThatThrownBy(
                () -> parser.parse(response)
        )
                .isInstanceOfSatisfying(
                        GeminiDecisionParseException.class,
                        exception ->
                                assertThat(exception.reason())
                                        .isEqualTo(
                                                GeminiDecisionParseException.Reason.MISSING_CONTENT
                                        )
                );
    }

    @Test
    void 깨진_envelope_JSON을_MALFORMED_JSON으로_거부한다() {

        assertThatThrownBy(
                () -> parser.parse("{ broken")
        )
                .isInstanceOfSatisfying(
                        GeminiDecisionParseException.class,
                        exception ->
                                assertThat(exception.reason())
                                        .isEqualTo(
                                                GeminiDecisionParseException.Reason.MALFORMED_JSON
                                        )
                );
    }

    @Test
    void 깨진_decision_JSON을_MALFORMED_JSON으로_거부한다() {

        String response =
                envelope(
                        "{ broken"
                );

        assertThatThrownBy(
                () -> parser.parse(response)
        )
                .isInstanceOfSatisfying(
                        GeminiDecisionParseException.class,
                        exception ->
                                assertThat(exception.reason())
                                        .isEqualTo(
                                                GeminiDecisionParseException.Reason.MALFORMED_JSON
                                        )
                );
    }

    @Test
    void decision이_JSON_object가_아니면_INVALID_STRUCTURE로_거부한다() {

        String response =
                envelope(
                        "[1, 2, 3]"
                );

        assertThatThrownBy(
                () -> parser.parse(response)
        )
                .isInstanceOfSatisfying(
                        GeminiDecisionParseException.class,
                        exception ->
                                assertThat(exception.reason())
                                        .isEqualTo(
                                                GeminiDecisionParseException.Reason.INVALID_STRUCTURE
                                        )
                );
    }

    @Test
    void 정상적인_structured_output을_파싱한다() {

        String decision = """
                {
                  "eventType": "REFLECTION",
                  "summary": "조용히 지난 일을 돌아보았다.",
                  "outcome": "복잡했던 감정이 조금 정리되었다.",
                  "importance": 5,
                  "narrativeAnchor": 3,
                  "energyChange": 0,
                  "moodChange": 2,
                  "memoryAbilityChange": 0,
                  "relationshipTargetCharacterId": null,
                  "affinityChange": 0,
                  "trustChange": 0,
                  "tensionChange": 0,
                  "memoryAccessChange": null
                }
                """;

        GeminiDecisionResponse result =
                parser.parse(
                        envelope(decision)
                );

        assertThat(result.eventType())
                .isEqualTo("REFLECTION");

        assertThat(result.summary())
                .isEqualTo(
                        "조용히 지난 일을 돌아보았다."
                );

        assertThat(result.outcome())
                .isEqualTo(
                        "복잡했던 감정이 조금 정리되었다."
                );

        assertThat(result.importance())
                .isEqualTo(5);

        assertThat(result.resolvedNarrativeAnchor())
                .isEqualTo(3);

        assertThat(result.moodChange())
                .isEqualTo(2);
    }

    @Test
    void relationshipFactChange_object를_파싱한다() {

        String decision = """
            {
              "eventType": "RELATIONSHIP_MILESTONE",
              "summary": "두 사람이 서로를 배우자로 명확히 인정했다.",
              "importance": 8,
              "narrativeAnchor": 9,
              "energyChange": 0,
              "moodChange": 0,
              "memoryAbilityChange": 0,
              "relationshipTargetCharacterId": null,
              "affinityChange": 0,
              "trustChange": 0,
              "tensionChange": 0,
              "memoryAccessChange": null,
              "memoryDetails": [],
              "relationshipFactChange": {
                "targetCharacterId": 2,
                "relation": "relationship_status",
                "value": "spouse"
              }
            }
            """;

        GeminiDecisionResponse result =
                parser.parse(
                        envelope(decision)
                );

        assertThat(result.relationshipFactChange())
                .isNotNull();

        assertThat(
                result.relationshipFactChange()
                        .targetCharacterId()
        ).isEqualTo(2L);

        assertThat(
                result.relationshipFactChange()
                        .relation()
        ).isEqualTo("relationship_status");

        assertThat(
                result.relationshipFactChange()
                        .value()
        ).isEqualTo("spouse");
    }

    @Test
    void relationshipFactChange_null을_파싱한다() {

        String decision = """
            {
              "eventType": "REFLECTION",
              "summary": "조용히 지난 일을 돌아보았다.",
              "importance": 5,
              "narrativeAnchor": 3,
              "energyChange": 0,
              "moodChange": 2,
              "memoryAbilityChange": 0,
              "relationshipTargetCharacterId": null,
              "affinityChange": 0,
              "trustChange": 0,
              "tensionChange": 0,
              "memoryAccessChange": null,
              "memoryDetails": [],
              "relationshipFactChange": null
            }
            """;

        GeminiDecisionResponse result =
                parser.parse(
                        envelope(decision)
                );

        assertThat(result.relationshipFactChange())
                .isNull();
    }

    private String envelope(
            String decisionJson
    ) {
        try {
            String escaped =
                    new ObjectMapper()
                            .writeValueAsString(
                                    decisionJson
                            );

            return """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": %s
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """.formatted(escaped);

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}