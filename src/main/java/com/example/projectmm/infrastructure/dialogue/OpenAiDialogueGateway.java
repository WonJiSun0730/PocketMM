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

    // Empty until a paid quality comparison authorizes a dedicated review model.
    @Value("${openai.continuity-model:}")
    private String continuityModel = "";

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
        var promptParts = promptBuilder.buildWorldRoomPromptParts(context);
        // Mock/custom builders may expose only the legacy combined method.
        if (promptParts == null) promptParts = new GeminiDialogueGateway.PromptParts(
                promptBuilder.buildWorldRoomSystemPrompt(context), "");
        String instructions = promptParts.stable() + """

                Return the response using the supplied strict JSON schema.
                Narrative-time rules:
                - elapsedSeconds is the amount of in-story time that actually passes during YOUR generated reply.
                - Estimate it from narrated events, not token count or response length.
                - Do not encode an explicit user-requested time skip here; the server handles it separately.
                - Use 0 for meta/OOC or effectively frozen time.
                Scene-location change rules:
                - locationChange is the final physical location at the end of YOUR reply, only if it explicitly changes.
                - Use an empty string when location is unchanged or uncertain.
                - Do not infer travel from plans, wishes, hypotheticals, or quoted dialogue.
                Player-state delta rules:
                - addedConditions/removedConditions describe explicit changes to the player character's conditions only.
                - addedInventory/removedInventory describe explicit acquisition, loss or consumption of player possessions only.
                - Never infer ownership from seeing, touching, mentioning, or planning to acquire an object.
                - Do not modify NPC state or inventory. Return empty arrays for unchanged or uncertain items.
                World-system change rules:
                - Current session world-system values are authoritative.
                - Return deltas only and use only supplied systemId values.
                - If uncertain or unchanged, omit the change.
                Cast-change rules:
                - Use only registered room-cast character IDs.
                - Active cast is authoritative for who is physically present at the start of this turn.
                - The prose and cast metadata MUST describe the same scene state.
                - If an absent registered room-cast character physically appears or speaks in your reply, include that character's ID in enterCharacterIds in the same response.
                - If an active registered character physically leaves the scene in your reply, include that character's ID in leaveCharacterIds in the same response.
                - Report only actual enter/leave changes; do not report unchanged presence.
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
                        "elapsedSeconds", Map.of("type", "integer", "minimum", 0, "maximum", 604800),
                        "locationChange", Map.of("type", "string"),
                        "addedConditions", Map.of("type", "array", "items", Map.of("type", "string")),
                        "removedConditions", Map.of("type", "array", "items", Map.of("type", "string")),
                        "addedInventory", Map.of("type", "array", "items", Map.of("type", "string")),
                        "removedInventory", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("reply", "enterCharacterIds", "leaveCharacterIds", "systemChanges", "elapsedSeconds", "locationChange", "addedConditions", "removedConditions", "addedInventory", "removedInventory")
        );

        var tokenEstimate = PromptTokenBreakdown.estimate(instructions + "\n" + promptParts.dynamic(), input, objectMapper.writeValueAsString(schema));
        input.add(0, Map.of("role", "developer", "content", promptParts.dynamic()));
        input.add(0, Map.of("role", "developer", "content", List.of(Map.of(
                "type", "input_text", "text", instructions,
                "prompt_cache_breakpoint", Map.of("mode", "explicit")))));
        usageTracker.recordPromptDiagnostics(context.sessionId(), model, instructions, promptParts.dynamic());
        Map<String, Object> request = Map.of(
                "model", model,
                "prompt_cache_options", Map.of("mode", "explicit"),
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

        String response = postResponse(context, model, request);

        return parseResponse(response, context, model, tokenEstimate);
    }

    private String postResponse(WorldRoomDialogueContext context, String model, Map<String,Object> request) {
        return usageTracker.accountRequest(context.sessionId(),"OPENAI",model, () -> restClient.post()
                .uri("/v1/responses")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(request)
                .retrieve()
                .body(String.class));
    }

    private String readPayload(String response, WorldRoomDialogueContext context, String model,
                               PromptTokenBreakdown breakdown) {
        JsonNode root = objectMapper.readTree(response);
        JsonNode usage = root.path("usage");
        if (usage.isObject()) usageTracker.record(
                context.sessionId(),
                "OPENAI",
                model,
                usage.path("input_tokens").asLong(0),
                usage.path("input_tokens_details").path("cached_tokens").asLong(0),
                usage.path("input_tokens_details").path("cache_write_tokens").isIntegralNumber()
                        ? usage.path("input_tokens_details").path("cache_write_tokens").asLong() : null,
                usage.path("output_tokens").asLong(0),
                usage.path("total_tokens").asLong(0), breakdown
        );
        else usageTracker.recordUnavailable(context.sessionId(),"OPENAI",model,"USAGE_NOT_REPORTED");
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

        return payload;
    }

    @Override
    public String reviewWorldRoomContinuity(WorldRoomDialogueContext context, String evidenceJson) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY is not configured");
        String model = context.aiModel() == null || context.aiModel().isBlank() ? defaultModel : context.aiModel().trim();
        if (continuityModel != null && !continuityModel.isBlank()) model = continuityModel.trim();
        String instructions = com.example.projectmm.application.dialogue.SceneContinuityPrompt.REVIEW;
        var parts = SceneContinuityRequest.parts(context, evidenceJson);
        var schema = SceneContinuityRequest.schema();
        // Authored reference remains user data; only the review policy has developer priority.
        var input = List.<Map<String,Object>>of(
                Map.of("role","developer","content",instructions),
                Map.of("role","user","content",List.of(Map.of("type","input_text","text",parts.stable(),
                        "prompt_cache_breakpoint",Map.of("mode","explicit")))),
                Map.of("role","user","content",parts.dynamic()));
        var estimate = PromptTokenBreakdown.estimate(instructions + "\n" + parts.stable(),
                List.of(Map.of("role","user","content",parts.dynamic())),objectMapper.writeValueAsString(schema));
        var request = Map.<String,Object>of("model",model,"input",input,
                "prompt_cache_options",Map.of("mode","explicit"),
                "reasoning",Map.of("effort","none"),"store",false,
                "text",Map.of("format",Map.of("type","json_schema","name","scene_continuity_review",
                        "strict",true,"schema",schema)));
        String response = postResponse(context,model,request);
        String payload = readPayload(response,context,model,estimate);
        var status = objectMapper.readTree(response).path("status").asText("completed");
        if (!status.equals("completed")) throw new IllegalStateException("OpenAI continuity review incomplete");
        return payload;
    }

    private WorldRoomTurn parseResponse(String response, WorldRoomDialogueContext context, String model) {
        return parseResponse(response, context, model, null);
    }

    private WorldRoomTurn parseResponse(String response, WorldRoomDialogueContext context, String model,
                                       PromptTokenBreakdown breakdown) {
        try {
            String payload = readPayload(response, context, model, breakdown);

            JsonNode turn = objectMapper.readTree(payload);
            String reply = turn.path("reply").asText();
            if (reply.isBlank()) throw new IllegalStateException("OpenAI Pocket reply is blank");



            return new WorldRoomTurn(
                    reply,
                    new WorldRoomCastChange(
                            readLongArray(turn.path("enterCharacterIds")),
                            readLongArray(turn.path("leaveCharacterIds"))
                    ),
                    readSystemChanges(turn.path("systemChanges"), context),
                    readElapsedSeconds(turn.path("elapsedSeconds")),
                    turn.path("locationChange").asText(""),
                    readStringArray(turn.path("addedConditions")),
                    readStringArray(turn.path("removedConditions")),
                    readStringArray(turn.path("addedInventory")),
                    readStringArray(turn.path("removedInventory"))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenAI Pocket response", e);
        }
    }

    private List<String> readStringArray(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank() && item.asText().length() <= 300 && values.size() < 20) {
                values.add(item.asText().trim());
            }
        }
        return values;
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
