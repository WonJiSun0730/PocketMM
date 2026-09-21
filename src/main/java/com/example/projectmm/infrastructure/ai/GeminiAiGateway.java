package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.simulation.StateDelta;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Profile("gemini")
public class GeminiAiGateway implements AiGateway {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiAiGateway(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model,
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
    public AiDecision decide(AiContext context) {

        Map<String, Object> request =
                createRequest(context);

        String response =
                restClient.post()
                        .uri(
                                "/v1beta/models/{model}:generateContent",
                                model
                        )
                        .header(
                                "x-goog-api-key",
                                apiKey
                        )
                        .body(request)
                        .retrieve()
                        .body(String.class);

        GeminiDecisionResponse decision =
                parseResponse(response);

        return new AiDecision(
                decision.eventType(),
                decision.summary(),
                decision.importance(),
                new StateDelta(
                        decision.energyChange(),
                        decision.moodChange()
                )
        );
    }

    private Map<String, Object> createRequest(
            AiContext context
    ) {
        String memoryPrompt =
                buildMemoryPrompt(context);

        String prompt = """
                You are a simulation decision component for PocketMM.

                Evaluate the character's current situation and produce
                one plausible event decision.

                Character: %s
                Energy: %d
                Mood: %d
                Simulation time: %s
                
                Relevant past memories: %s

                Candidate event type: %s
                Candidate summary: %s

                Keep state changes conservative.
                Energy and mood changes should normally remain between
                -20 and 20.
                """
                .formatted(
                        context.characterName(),
                        context.energy(),
                        context.mood(),
                        context.simulatedAt(),
                        memoryPrompt,
                        context.eventType(),
                        context.eventSummary()
                );

        Map<String, Object> schema =
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "eventType", Map.of(
                                        "type", "string"
                                ),
                                "summary", Map.of(
                                        "type", "string"
                                ),
                                "importance", Map.of(
                                        "type", "integer"
                                ),
                                "energyChange", Map.of(
                                        "type", "integer"
                                ),
                                "moodChange", Map.of(
                                        "type", "integer"
                                )
                        ),
                        "required", List.of(
                                "eventType",
                                "summary",
                                "importance",
                                "energyChange",
                                "moodChange"
                        )
                );

        return Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of(
                                                "text",
                                                prompt
                                        )
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "responseMimeType",
                        "application/json",
                        "responseJsonSchema",
                        schema
                )
        );
    }

    private String buildMemoryPrompt(
            AiContext context
    ) {
        if (context.memories() == null
                || context.memories().isEmpty()) {

            return "No relevant past memories.";
        }

        return context.memories().stream()
                .map(memory ->
                        "- "
                                + memory.summary()
                                + " (strength: "
                                + memory.strength()
                                + ", importance: "
                                + memory.importance()
                                + ")"
                )
                .collect(
                        java.util.stream.Collectors.joining("\n")
                );
    }

    private GeminiDecisionResponse parseResponse(
            String response
    ) {
        try {
            JsonNode root =
                    objectMapper.readTree(response);

            String json =
                    root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text")
                            .asText();

            if (json.isBlank()) {
                throw new IllegalStateException(
                        "Gemini returned an empty response"
                );
            }

            return objectMapper.readValue(
                    json,
                    GeminiDecisionResponse.class
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse Gemini response",
                    e
            );
        }
    }
}