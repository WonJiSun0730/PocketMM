package com.example.projectmm.infrastructure.time;

import com.example.projectmm.application.time.AiTimeParser;
import com.example.projectmm.domain.time.AiTimeParseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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
            @Value("${gemini.time-model}") String model,
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

        } catch (HttpClientErrorException.TooManyRequests e) {

            if (isDailyQuotaExhausted(e)) {
                System.out.println(
                        "[GeminiTimeParser] daily quota exhausted - skipping AI time parsing"
                );
            } else {
                System.out.println(
                        "[GeminiTimeParser] rate limited - skipping AI time parsing"
                );
            }

            return noMatch();

        } catch (HttpServerErrorException.ServiceUnavailable e) {

            System.out.println(
                    "[GeminiTimeParser] Gemini unavailable - skipping AI time parsing"
            );

            return noMatch();

        } catch (Exception e) {

            System.out.println(
                    "[GeminiTimeParser] unexpected parse failure: "
                            + e.getMessage()
            );

            return noMatch();
        }
    }

    private boolean isDailyQuotaExhausted(
            HttpClientErrorException.TooManyRequests e
    ) {
        String responseBody = e.getResponseBodyAsString();

        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }

        return responseBody.contains(
                "GenerateRequestsPerDayPerProjectPerModel"
        );
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
                  "precision": "APPROXIMATE",
                  "durationScale": "UNSPECIFIED",
                  "dayPart": null,
                  "targetTime": null,
                  "confidence": 0.95,
                  "sourceExpression": "두 시간 뒤"
                }

                Allowed precision values:
                - EXACT
                - APPROXIMATE
                - AMBIGUOUS

                Allowed type values:
                - RELATIVE
                - NEXT_DAY
                - SAME_DAY_PART
                - SCENE_TIME
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
                - Use SCENE_TIME when the current scene's final clock is explicitly stated,
                  or a compound transition combines a day jump, clock, and elapsed activity.
                  Return targetTime as an ISO local date-time, e.g. "2026-10-08T15:25:00".
                  Resolve every event in narrative order; never ignore the day jump.
                  Set amount/unit/dayPart to null for SCENE_TIME. For all other types targetTime=null.
                - "다시 상담소에 돌아오니 3시 25분이다" establishes the current clock;
                  retain the current date and infer omitted AM/PM from the current scene.
                - "상담은 3시 반이다" is an appointment, not the current clock: matched=false.
                - "3일 후, 수험생 B는 오후 3시에 잠들어 6시간정도 잔 것 같다" means
                  move forward three days, set bedtime to 15:00, then add roughly six hours.
                  Return that day's 21:00 as the approximate baseline, never just a day part.
                - Explicit final clock readings override estimates of how long earlier activities took.
                - A reported current clock equal to the server clock is still matched=true, EXACT.
                - RELATIVE means a duration passes from the current scene.
                - NEXT_DAY means the scene moves to the following day.
                - SAME_DAY_PART means the scene moves forward to a later
                  part of the same day.
                - Confidence measures certainty that the CURRENT scene actually advances,
                  not certainty about the number of elapsed minutes. Do not lower it
                  merely because the elapsed duration is unquantified.
                - If no actual time progression is established,
                  return matched=false and type=UNKNOWN.

                Precision rules:
                - EXACT means the narration establishes a specific elapsed duration
                  as an objective fact.
                - APPROXIMATE means a duration is given with uncertainty or approximation,
                  such as "정도", "쯤", "가량", "것 같다", or similar wording.
                - AMBIGUOUS means time clearly progressed but the elapsed duration
                  cannot be reliably quantified.
                - Distinguish objective narration from a character's subjective estimate.
                - "정확히 한 시간이 지났다" should be EXACT.
                - "한 시간 정도 지난 것 같다" should be APPROXIMATE.
                - "한참 지났다" should be AMBIGUOUS.

                Unquantified relative progression:
                - For RELATIVE + AMBIGUOUS, return amount=null and unit=null.
                  Never invent a numeric duration. The server chooses elapsed minutes.
                - Classify durationScale from the user's wording and activity context:
                  SHORT: a brief interval, e.g. "잠깐 기다렸다", "잠시 쉬었다".
                  MEDIUM: a moderate interval, e.g. "얼마간 일했다".
                  LONG: an extended interval, e.g. "한참 일했다",
                    "열심히 일했다. 한참 시간이 흐른 뒤 시계를 확인했다".
                  UNSPECIFIED: even the relative scale cannot be inferred reliably.
                - Use UNSPECIFIED for other types or precisions.
                - An explicit elapsed interval can have high confidence even with
                  durationScale=UNSPECIFIED. Do not confuse missing scale with no progression.
                - "나중에 한참 일해야겠다" is a plan: matched=false, type=UNKNOWN.
                - "어제 한참 일했다" is a past reference: matched=false, type=UNKNOWN.
                - "한참 기다린다면 피곤하겠지" is hypothetical: matched=false, type=UNKNOWN.
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
                    parseEnum(
                            AiTimeParseResult.TimePrecision.class,
                            nullableText(result, "precision"),
                            AiTimeParseResult.TimePrecision.AMBIGUOUS
                    ),
                    nullableDouble(
                            result,
                            "confidence"
                    ),
                    nullableText(
                            result,
                            "sourceExpression"
                    ),
                    parseEnum(
                            AiTimeParseResult.DurationScale.class,
                            nullableText(result, "durationScale"),
                            AiTimeParseResult.DurationScale.UNSPECIFIED
                    ),
                    parseTargetTime(nullableText(result, "targetTime"))
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

    private LocalDateTime parseTargetTime(String value) {
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException invalidTime) {
            return null;
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
                AiTimeParseResult.TimePrecision.AMBIGUOUS,
                0.0,
                null,
                AiTimeParseResult.DurationScale.UNSPECIFIED
        );
    }
}
