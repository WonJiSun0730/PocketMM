package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.memory.MemoryEmbeddingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
@Profile("gemini")
public class GeminiMemoryEmbeddingProvider
        implements MemoryEmbeddingProvider {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiMemoryEmbeddingProvider(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.embedding-model:gemini-embedding-001}")
            String model,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;

        this.restClient =
                RestClient.builder()
                        .baseUrl(
                                "https://generativelanguage.googleapis.com"
                        )
                        .build();
    }

    @Override
    public List<Double> embed(
            String text
    ) {
        if (text == null
                || text.isBlank()) {
            return List.of();
        }

        Map<String, Object> request =
                createRequest(text);

        String response =
                restClient.post()
                        .uri(
                                "/v1beta/models/{model}:embedContent",
                                model
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
                        .body(request)
                        .retrieve()
                        .body(String.class);

        return parseEmbedding(response);
    }

    Map<String, Object> createRequest(
            String text
    ) {
        return Map.of(
                "model",
                "models/" + model,
                "taskType",
                "SEMANTIC_SIMILARITY",
                "content",
                Map.of(
                        "parts",
                        List.of(
                                Map.of(
                                        "text",
                                        text
                                )
                        )
                )
        );
    }

    List<Double> parseEmbedding(
            String response
    ) {
        if (response == null
                || response.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root =
                    objectMapper.readTree(
                            response
                    );

            JsonNode values =
                    root.path("embedding")
                            .path("values");

            if (!values.isArray()
                    || values.isEmpty()) {
                return List.of();
            }

            return values.valueStream()
                    .filter(JsonNode::isNumber)
                    .map(JsonNode::asDouble)
                    .toList();

        } catch (Exception ignored) {
            return List.of();
        }
    }
}