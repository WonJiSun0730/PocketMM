package com.example.projectmm.infrastructure.time;

import com.example.projectmm.application.time.AiTimeParser;
import com.example.projectmm.domain.time.AiTimeParseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Profile("gemini")
public class GeminiTimeParser implements AiTimeParser {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiTimeParser(
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
    public AiTimeParseResult parse(
            String userInput,
            LocalDateTime currentTime
    ) {
        if (userInput == null || userInput.isBlank()) {
            return noMatch();
        }

        try {
            Map<String, Object> request =
                    createRequest(
                            userInput,
                            currentTime
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

            return parseResponse(response);

        } catch (Exception e) {
            System.out.println(
                    "[GeminiTimeParser] parse failed: "
                            + e.getMessage()
            );

            return noMatch();
        }
    }

    private Map<String, Object> createRequest(
            String userInput,
            LocalDateTime currentTime
    ) {
        String prompt = """
                You analyze time progression in a prose roleplay.

                Current simulation time:
                %s

                User input:
                %s

                Determine whether the user input establishes that the
                CURRENT roleplay scene advances forward in time.

                Return JSON only with this structure:

                {
                  "matched": true,
                  "type": "RELATIVE",
                  "amount": 2,
                  "unit": "HOUR",
                  "dayPart": null,
                  "confidence": 0.95,
                  "sourceExpression": "두 시간 뒤"
                }

                Allowed type values:
                - RELATIVE
                - NEXT_DAY
                - SAME_DAY_PART
                - UNKNOWN

                Allowed unit values:
                - MINUTE
                - HOUR
                - DAY

                Allowed dayPart values:
                - MORNING
                - NOON
                - AFTERNOON
                - EVENING
                - NIGHT

                Rules:
                - Detect actual progression of the current roleplay scene only.
                - Do not treat memories or past references as progression.
                - Do not treat hypothetical future events as progression.
                - Do not treat schedules or plans as progression.
                - Do not treat quoted speech about time as progression.
                - Do not return an absolute date or absolute time.
                - RELATIVE means a duration passes from the current scene.
                - NEXT_DAY means the scene moves to the following day.
                - SAME_DAY_PART means the scene moves forward to a later
                  part of the same day.
                - If the expression is ambiguous, lower confidence.
                - If no actual time progression is established,
                  return matched=false and type=UNKNOWN.
                """
                .formatted(
                        currentTime,
                        userInput
                );

        return Map.of(
                "contents",
                List.of(
                        Map.of(
                                "role",
                                "user",
                                "parts",
                                List.of(
                                        Map.of(
                                                "text",
                                                prompt
                                        )
                                )
                        )
                ),
                "generationConfig",
                Map.of(
                        "responseMimeType",
                        "application/json"
                )
        );
    }

    private AiTimeParseResult parseResponse(
            String response
    ) {
        if (response == null || response.isBlank()) {
            return noMatch();
        }

        try {
            JsonNode root =
                    objectMapper.readTree(response);

            String text =
                    root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text")
                            .asText();

            if (text == null || text.isBlank()) {
                return noMatch();
            }

            JsonNode result =
                    objectMapper.readTree(text);

            boolean matched =
                    result.path("matched")
                            .asBoolean(false);

            if (!matched) {
                return noMatch();
            }

            return new AiTimeParseResult(
                    true,
                    parseEnum(
                            AiTimeParseResult.TimeType.class,
                            nullableText(result, "type"),
                            AiTimeParseResult.TimeType.UNKNOWN
                    ),
                    nullableLong(result, "amount"),
                    parseEnum(
                            AiTimeParseResult.TimeUnit.class,
                            nullableText(result, "unit"),
                            null
                    ),
                    parseEnum(
                            AiTimeParseResult.DayPart.class,
                            nullableText(result, "dayPart"),
                            null
                    ),
                    nullableDouble(
                            result,
                            "confidence"
                    ),
                    nullableText(
                            result,
                            "sourceExpression"
                    )
            );

        } catch (Exception e) {
            return noMatch();
        }
    }

    private <T extends Enum<T>> T parseEnum(
            Class<T> enumType,
            String value,
            T fallback
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(
                    enumType,
                    value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private Long nullableLong(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.path(field);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asLong();
    }

    private Double nullableDouble(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.path(field);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asDouble();
    }

    private String nullableText(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.path(field);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        String text = value.asText();

        return text.isBlank()
                ? null
                : text;
    }

    private AiTimeParseResult noMatch() {
        return new AiTimeParseResult(
                false,
                AiTimeParseResult.TimeType.UNKNOWN,
                null,
                null,
                null,
                0.0,
                null
        );
    }
}