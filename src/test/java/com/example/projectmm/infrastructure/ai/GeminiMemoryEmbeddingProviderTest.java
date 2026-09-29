package com.example.projectmm.infrastructure.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiMemoryEmbeddingProviderTest {

    private final GeminiMemoryEmbeddingProvider provider =
            new GeminiMemoryEmbeddingProvider(
                    "test-key",
                    "gemini-embedding-001",
                    new ObjectMapper()
            );

    @Test
    void createsSemanticSimilarityRequest() {

        Map<String, Object> request =
                provider.createRequest(
                        "TYPE_PREFERENCE SUBJECT_SELF RELATION_LIKE"
                );

        assertThat(request)
                .containsEntry(
                        "model",
                        "models/gemini-embedding-001"
                )
                .containsEntry(
                        "taskType",
                        "SEMANTIC_SIMILARITY"
                );

        assertThat(request)
                .containsKey("content");
    }

    @Test
    void parsesEmbeddingValues() {

        String response = """
                {
                  "embedding": {
                    "values": [
                      0.125,
                      -0.25,
                      0.5
                    ]
                  }
                }
                """;

        List<Double> embedding =
                provider.parseEmbedding(
                        response
                );

        assertThat(embedding)
                .containsExactly(
                        0.125,
                        -0.25,
                        0.5
                );
    }

    @Test
    void invalidResponseProducesEmptyEmbedding() {

        assertThat(
                provider.parseEmbedding(
                        "{}"
                )
        ).isEmpty();

        assertThat(
                provider.parseEmbedding(
                        null
                )
        ).isEmpty();
    }
}