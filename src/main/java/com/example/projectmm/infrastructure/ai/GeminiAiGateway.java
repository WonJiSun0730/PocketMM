package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.StateDelta;
import com.example.projectmm.domain.memory.MemoryAccessChange;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.LinkedHashMap;
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

        return toDecision(decision);
    }

    AiDecision toDecision(GeminiDecisionResponse decision) {
        return new AiDecision(
                decision.eventType(),
                decision.summary(),
                decision.importance(),
                decision.resolvedNarrativeAnchor(),
                new StateDelta(
                        decision.energyChange(),
                        decision.moodChange(),
                        decision.memoryAbilityChange()
                ),
                decision.relationshipTargetCharacterId(),
                new RelationshipDelta(
                        decision.affinityChange(),
                        decision.trustChange(),
                        decision.tensionChange()
                ),
                memoryAccessChange(decision.memoryAccessChange())
        );
    }

    private MemoryAccessChange memoryAccessChange(JsonNode node) {
        if (node == null || node.isNull()) return null;
        // 잘못된 타입·추가 대상 ID를 조용히 무시하지 않고 validator가 거부할 제안으로 전달한다.
        if (!node.isObject() || node.size() < 2 || node.size() > 3
                || !node.path("scope").isString() || !node.path("accessLevel").isString()
                || node.properties().stream().anyMatch(entry -> !List.of("scope", "accessLevel", "since").contains(entry.getKey()))
                || (node.has("since") && !node.path("since").isNull() && !node.path("since").isString())) {
            return new MemoryAccessChange(null, null, null);
        }
        return new MemoryAccessChange(node.path("scope").asText(), node.path("accessLevel").asText(),
                !node.has("since") || node.path("since").isNull() ? null : node.path("since").asText());
    }

    private Map<String, Object> createRequest(
            AiContext context
    ) {
        String memoryPrompt =
                buildMemoryPrompt(context);

        String relationshipPrompt =
                buildRelationshipPrompt(context);

        String prompt = """
                You are a simulation decision component for PocketMM.

                Evaluate the character's current situation and produce
                one plausible event decision.

                Character: %s
                Energy: %d
                Mood: %d
                Simulation time: %s
                
                Relevant past memories:
                %s
                
                Current relationships:
                %s
                
                Candidate event type: %s
                Candidate summary: %s
                
                Narrative anchor represents how strongly this event should be
                preserved for long-term narrative continuity.
                
                Judge narrativeAnchor independently from importance.
                
                Use this scale:
                0 = no long-term narrative preservation needed.
                1-3 = ordinary or temporary event; forgetting it would barely affect continuity.
                4-6 = meaningful for future behavior or relationships.
                7-8 = important relationship turning point, strong promise,
                      major personal experience, or repeatedly referenced core event.
                9-10 = identity-defining fact or event for the character,
                       relationship, or world; forgetting it could break long-term continuity.
                
                Do not assign 9-10 merely because an event is emotionally intense.
                A highly intense one-time event may have high importance but a lower
                narrativeAnchor. A lasting relationship-defining event may have a high
                narrativeAnchor independently of its immediate intensity.

                Keep state changes conservative.
                Energy, mood, and memory ability changes should normally
                remain between -20 and 20.
                
                Memory ability represents the character's current ability
                to recall existing memories.
                Use 0 unless the event plausibly changes that ability.
                Do not decrease memory ability merely because time passed
                or because a memory itself became weaker.

                Memory access is separate from memory ability. Do not simulate amnesia by
                lowering memory ability. Default memoryAccessChange to null.
                Only an established narrative consequence may change access to existing memories.
                When needed, use {"scope":"ALL" or "SINCE", "accessLevel":"EXPLICIT",
                "since":null or an ISO local datetime}. Levels: EXPLICIT (restore details),
                IMPLICIT (familiarity only), RESIDUAL (emotional residue), BLOCKED (no recall).
                ALL requires since=null; SINCE includes the boundary timestamp and requires
                a supported timestamp no later than this event. Never guess a missing cutoff.
                This affects ONLY the current character. Never include characterId or memoryId.
                
                Relationship changes represent how this event affects
                the character's relationship with the relevant person.
                Affinity, trust, and tension changes should normally remain
                between -10 and 10.
                
                When relationship changes are non-zero, set
                relationshipTargetCharacterId to the ID of the affected person
                from Current relationships.
                
                If no relationship is affected,
                relationshipTargetCharacterId must be null.
                
                If the event does not meaningfully affect a relationship,
                return 0 for all relationship changes.
                """
                .formatted(
                        context.characterName(),
                        context.energy(),
                        context.mood(),
                        context.simulatedAt(),
                        memoryPrompt,
                        relationshipPrompt,
                        context.eventType(),
                        context.eventSummary()
                );

                Map<String, Object> schemaProperties =
                        new LinkedHashMap<>();

                schemaProperties.put(
                        "eventType",
                        Map.of("type", "string")
                );

                schemaProperties.put(
                        "summary",
                        Map.of("type", "string")
                );

                schemaProperties.put(
                        "importance",
                        Map.of("type", "integer")
                );

                schemaProperties.put(
                        "narrativeAnchor",
                        Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "maximum", 10
                        )
                );

                schemaProperties.put(
                        "energyChange",
                        Map.of("type", "integer")
                );

                schemaProperties.put(
                        "moodChange",
                        Map.of("type", "integer")
                );

                schemaProperties.put(
                        "memoryAbilityChange",
                        Map.of("type", "integer")
                );

                schemaProperties.put(
                        "relationshipTargetCharacterId",
                        Map.of(
                                "type",
                                List.of(
                                        "integer",
                                        "null"
                                )
                        )
                );

                schemaProperties.put(
                        "affinityChange",
                        Map.of("type", "integer")
                );

                schemaProperties.put(
                        "trustChange",
                        Map.of("type", "integer")
                );

                schemaProperties.put(
                        "tensionChange",
                        Map.of("type", "integer")
                );

                Map<String, Object> schema =
                        new LinkedHashMap<>();

                schema.put("type", "object");
                schema.put("properties", schemaProperties);
                schema.put(
                        "required",
                        List.of(
                                "eventType",
                                "summary",
                                "importance",
                                "narrativeAnchor",
                                "energyChange",
                                "moodChange",
                                "memoryAbilityChange",
                                "relationshipTargetCharacterId",
                                "affinityChange",
                                "trustChange",
                                "tensionChange"
                        )
                );

        var properties = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) schema.get("properties")).forEach((key, value) -> properties.put((String) key, value));
        properties.put("memoryAccessChange", Map.of(
                "type", List.of("object", "null"), "additionalProperties", false,
                "properties", Map.of(
                        "scope", Map.of("type", "string", "enum", List.of("ALL", "SINCE")),
                        "accessLevel", Map.of("type", "string", "enum", List.of("EXPLICIT", "IMPLICIT", "RESIDUAL", "BLOCKED")),
                        "since", Map.of("type", List.of("string", "null"))),
                "required", List.of("scope", "accessLevel", "since")));
        schema = new LinkedHashMap<>(schema);
        schema.put("properties", properties);

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

    private String buildRelationshipPrompt(
            AiContext context
    ) {
        if (context.relationships() == null
                || context.relationships().isEmpty()) {

            return "No known relationships.";
        }

        return context.relationships().stream()
                .map(relationship ->
                        "- ID "
                                + relationship.characterId()
                                + ": "
                                + relationship.characterName()
                                + " (affinity: "
                                + relationship.affinity()
                                + ", trust: "
                                + relationship.trust()
                                + ", tension: "
                                + relationship.tension()
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
