package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import com.example.projectmm.domain.dialogue.WorldRoomSystemChange;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OpenAiDialogueGateway implements DialogueGateway {

    private final String apiKey;
    private final String defaultModel;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiDialogueGateway promptBuilder;
    private final AiUsageTracker usageTracker;

    public OpenAiDialogueGateway(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-5.6-luna}") String defaultModel,
            ObjectMapper objectMapper,
            GeminiDialogueGateway promptBuilder,
            AiUsageTracker usageTracker
    ) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.usageTracker = usageTracker;
        this.restClient = RestClient.builder().baseUrl("https://api.openai.com").build();
    }

    @Override
    public String generate(DialogueContext context, String userMessage) {
        throw new UnsupportedOperationException("Legacy dialogue is not routed through OpenAI yet");
    }

    @Override
    public WorldRoomTurn generateWorldRoomTurn(WorldRoomDialogueContext context, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }

        String model = context.aiModel() == null || context.aiModel().isBlank()
                ? defaultModel
                : context.aiModel().trim();
        String resolvedUserMessage = userMessage == null ? "" : userMessage.trim();
        String instructions = promptBuilder.buildWorldRoomSystemPrompt(context) + """

                Return the response using the supplied strict JSON schema.
                Narrative-time rules:
                - elapsedSeconds is the amount of in-story time that actually passes during YOUR generated reply.
                - Estimate it from narrated events, not token count or response length.
                - Do not encode an explicit user-requested time skip here; the server handles it separately.
                - Use 0 for meta/OOC or effectively frozen time.
                World-system change rules:
                - Current session world-system values are authoritative.
                - Return deltas only and use only supplied systemId values.
                - If uncertain or unchanged, omit the change.
                Cast-change rules:
                - Use only registered room-cast character IDs.
                - Report only actual enter/leave changes.
                """;

        List<Map<String, Object>> input = new ArrayList<>();
        var history = context.recentMessages();
        int historySize = history == null ? 0 : history.size();
        int lastHistoryIndex = historySize - 1;
        for (int i = 0; i < historySize; i++) {
            var message = history.get(i);
            if (i == lastHistoryIndex
                    && message.role() == com.example.projectmm.domain.chat.ChatRole.USER
                    && message.content().trim().equals(resolvedUserMessage)) {
                continue;
            }
            input.add(Map.of(
                    "role", message.role() == com.example.projectmm.domain.chat.ChatRole.USER ? "user" : "assistant",
                    "content", message.content()
            ));
        }
        input.add(Map.of("role", "user", "content", resolvedUserMessage));

        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "reply", Map.of("type", "string"),
                        "enterCharacterIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                        "leaveCharacterIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                        "systemChanges", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "properties", Map.of(
                                                "systemId", Map.of("type", "integer"),
                                                "delta", Map.of("type", "integer")
                                        ),
                                        "required", List.of("systemId", "delta")
                                )
                        ),
                        "elapsedSeconds", Map.of("type", "integer", "minimum", 0, "maximum", 604800)
                ),
                "required", List.of("reply", "enterCharacterIds", "leaveCharacterIds", "systemChanges", "elapsedSeconds")
        );

        Map<String, Object> request = Map.of(
                "model", model,
                "instructions", instructions,
                "input", input,
                "reasoning", Map.of("effort", "none"),
                "store", false,
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "pocket_turn",
                        "strict", true,
                        "schema", schema
                ))
        );

        String response = restClient.post()
                .uri("/v1/responses")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(request)
                .retrieve()
                .body(String.class);

        return parseResponse(response, context, model);
    }

    private WorldRoomTurn parseResponse(String response, WorldRoomDialogueContext context, String model) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String payload = null;
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode part : item.path("content")) {
                    if ("output_text".equals(part.path("type").asText())) {
                        payload = part.path("text").asText();
                        break;
                    }
                }
                if (payload != null) break;
            }
            if (payload == null || payload.isBlank()) {
                throw new IllegalStateException("OpenAI response has no output_text");
            }

            JsonNode turn = objectMapper.readTree(payload);
            String reply = turn.path("reply").asText();
            if (reply.isBlank()) throw new IllegalStateException("OpenAI Pocket reply is blank");

            JsonNode usage = root.path("usage");
            usageTracker.record(
                    context.sessionId(),
                    "OPENAI",
                    model,
                    usage.path("input_tokens").asLong(0),
                    usage.path("input_tokens_details").path("cached_tokens").asLong(0),
                    usage.path("output_tokens").asLong(0),
                    usage.path("total_tokens").asLong(0)
            );

            return new WorldRoomTurn(
                    reply,
                    new WorldRoomCastChange(
                            readLongArray(turn.path("enterCharacterIds")),
                            readLongArray(turn.path("leaveCharacterIds"))
                    ),
                    readSystemChanges(turn.path("systemChanges"), context),
                    readElapsedSeconds(turn.path("elapsedSeconds"))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenAI Pocket response", e);
        }
    }

    private long readElapsedSeconds(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) return 0;
        long seconds = node.asLong();
        return seconds < 0 || seconds > 604_800L ? 0 : seconds;
    }

    private List<Long> readLongArray(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<Long> result = new ArrayList<>();
        node.forEach(item -> { if (item.canConvertToLong()) result.add(item.asLong()); });
        return List.copyOf(result);
    }

    private List<WorldRoomSystemChange> readSystemChanges(JsonNode node, WorldRoomDialogueContext context) {
        if (!node.isArray()) return List.of();
        var allowedIds = context.sessionSystems().stream()
                .map(system -> system.systemId())
                .collect(Collectors.toSet());
        var seen = new HashSet<Long>();
        var result = new ArrayList<WorldRoomSystemChange>();
        for (JsonNode item : node) {
            long id = item.path("systemId").asLong(Long.MIN_VALUE);
            int delta = item.path("delta").asInt(0);
            if (allowedIds.contains(id) && seen.add(id) && delta != 0) {
                result.add(new WorldRoomSystemChange(id, delta));
            }
        }
        return List.copyOf(result);
    }
}
