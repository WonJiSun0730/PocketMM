package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.chat.CatchUpNarrativeGateway;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.simulation.ResolvedEvent;
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
public class GeminiCatchUpNarrativeGateway
        implements CatchUpNarrativeGateway {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiCatchUpNarrativeGateway(
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
    public String narrate(
            Character character,
            List<ResolvedEvent> events
    ) {
        if (events == null || events.isEmpty()) {
            return "";
        }

        String eventText =
                events.stream()
                        .sorted(
                                java.util.Comparator.comparing(
                                        ResolvedEvent::occurredAt
                                )
                        )
                        .map(event ->
                                "- time: "
                                        + event.occurredAt()
                                        + "\n  type: "
                                        + event.eventType()
                                        + "\n  summary: "
                                        + event.summary()
                        )
                        .collect(
                                java.util.stream.Collectors.joining(
                                        "\n"
                                )
                        );

        String prompt =
                """
                You are writing a reconnect narrative for a character simulation.

                Character:
                %s

                Events that actually occurred:
                %s

                Write one natural Korean narrative passage describing what happened
                while the user was away.

                Rules:
                - Write approximately 300 to 500 Korean characters.
                - Preserve the chronological order of the supplied events.
                - Describe ONLY events supported by the supplied event data.
                - Do not invent new meetings, promises, conflicts, relationships,
                  decisions, locations, possessions, or major actions.
                - You may add minor sensory or transitional description only when it
                  does not change what actually happened.
                - Connect related events into a continuous scene when appropriate.
                - Do not present the result as a list, report, log, or system message.
                - Do not prefix the passage with the character name and a dash.
                - Do not explain these rules.
                - Output only the narrative passage.
                """
                        .formatted(
                                character.getName(),
                                eventText
                        );

        Map<String, Object> request =
                Map.of(
                        "contents",
                        List.of(
                                Map.of(
                                        "parts",
                                        List.of(
                                                Map.of(
                                                        "text",
                                                        prompt
                                                )
                                        )
                                )
                        )
                );

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

        try {
            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode text =
                    root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text");

            if (text.isMissingNode()
                    || text.isNull()
                    || text.asText().isBlank()) {
                throw new IllegalStateException(
                        "Gemini catch-up narrative response is empty"
                );
            }

            return text.asText().trim();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse Gemini catch-up narrative response",
                    e
            );
        }
    }
}