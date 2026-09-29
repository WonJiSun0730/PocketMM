package com.example.projectmm.infrastructure.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
class GeminiMemoryEmbeddingLiveTest {

    @Test
    void realGeminiEmbeddingReturnsVector() {

        String apiKey =
                System.getenv(
                        "GEMINI_API_KEY"
                );

        assertThat(apiKey)
                .as("GEMINI_API_KEY environment variable")
                .isNotBlank();

        GeminiMemoryEmbeddingProvider provider =
                new GeminiMemoryEmbeddingProvider(
                        apiKey,
                        "gemini-embedding-001",
                        new ObjectMapper()
                );

        List<Double> embedding =
                provider.embed(
                        "TYPE_PREFERENCE SUBJECT_SELF RELATION_LIKE OBJECT_COFFEE"
                );

        assertThat(embedding)
                .isNotEmpty();

        assertThat(embedding.size())
                .isGreaterThan(100);

        assertThat(embedding)
                .allSatisfy(value ->
                        assertThat(value)
                                .isFinite()
                );
    }
}