package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.chat.ChatRole;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Task-specific evidence and schema; never calls the fiction prompt builder. */
final class SceneContinuityRequest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private SceneContinuityRequest() {}

    record Parts(String stable, String dynamic) {}

    static Parts parts(WorldRoomDialogueContext context, String evidenceJson) {
        var combined = JSON.readTree(evidence(context, evidenceJson));
        var dynamic = (tools.jackson.databind.node.ObjectNode) combined;
        var stable = dynamic.remove("stableReference");
        return new Parts(JSON.writeValueAsString(Map.of("stableReference", stable)),
                JSON.writeValueAsString(dynamic));
    }

    static String evidence(WorldRoomDialogueContext context, String evidenceJson) {
        var data = JSON.readTree(evidenceJson);
        if (!data.isObject()) throw new IllegalArgumentException("Continuity evidence must be an object");
        var root = (tools.jackson.databind.node.ObjectNode) data;
        boolean bootstrap = "BOOTSTRAP".equals(root.path("mode").asText());
        String user = root.path("userInput").asText("");
        var reference = new LinkedHashMap<String, Object>();
        reference.put("world", context.world());
        reference.put("playerProfile", context.playerProfile());
        reference.put("introTitle", context.introTitle());
        reference.put("introContent", context.introContent());
        reference.put("userNote", context.userNote());
        reference.put("pocketPlayGuide", context.pocketPlayGuide());
        reference.put("noControlPlayer", context.noControlPlayer());
        reference.put("roomCast", context.roomCast().stream()
                .sorted(Comparator.comparing(WorldRoomCharacterContext::characterId))
                .map(c -> {
                    var profile = new LinkedHashMap<String, Object>();
                    profile.put("characterId", c.characterId()); profile.put("characterName", c.characterName());
                    profile.put("profile", c.profile()); return profile;
                }).toList());
        reference.put("sessionSystems", context.sessionSystems().stream()
                .sorted(Comparator.comparing(WorldRoomSystemContext::systemId))
                .map(s -> {
                    var definition = new LinkedHashMap<String, Object>();
                    definition.put("systemId", s.systemId()); definition.put("name", s.name());
                    definition.put("minValue", s.minValue()); definition.put("maxValue", s.maxValue());
                    definition.put("changeCondition", s.changeCondition()); definition.put("states", s.states());
                    return definition;
                }).toList());
        var dynamicReference = new LinkedHashMap<String, Object>();
        dynamicReference.put("simulatedAt", context.simulatedAt());
        dynamicReference.put("roomCastState", context.roomCast().stream().map(c -> Map.of(
                "characterId", c.characterId(), "energy", c.energy(), "mood", c.mood(),
                "active", c.active(), "departed", c.departed())).toList());
        dynamicReference.put("systemValues", context.sessionSystems().stream().map(s -> Map.of(
                "systemId", s.systemId(), "currentValue", s.currentValue())).toList());
        dynamicReference.put("playerState", context.playerState());
        dynamicReference.put("recalledMemories", context.recalledMemories());
        // Preserve authored profiles/lore and relevant knowledge. Facts already occur in data.facts.
        // Historical bootstrap must not see later recent dialogue outside its chronological batch.
        var recent = new ArrayList<DialogueMessageContext>();
        if (!bootstrap && context.recentMessages() != null) {
            recent.addAll(context.recentMessages());
            if (!recent.isEmpty() && recent.getLast().role() == ChatRole.USER
                    && recent.getLast().content().trim().equals(user.trim())) recent.removeLast();
        }
        dynamicReference.put("recentMessages", recent);
        if (bootstrap) root.put("userInput", ""); // USER passages already occur in history with their roles.
        root.set("reference", JSON.valueToTree(dynamicReference));
        var ordered = new LinkedHashMap<String, Object>();
        ordered.put("stableReference", reference);
        root.properties().forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return JSON.writeValueAsString(ordered);
    }

    static Map<String, Object> schema() {
        var cast = object(Map.of("enterCharacterIds", integers(), "leaveCharacterIds", integers()));
        var system = object(Map.of("systemId", Map.of("type","integer"), "delta", Map.of("type","integer")));
        var turnProperties = new LinkedHashMap<String,Object>();
        turnProperties.put("reply", text());
        turnProperties.put("castChange", cast);
        turnProperties.put("systemChanges", array(system));
        turnProperties.put("elapsedSeconds", Map.of("type","integer","minimum",0,"maximum",604800));
        turnProperties.put("locationChange", Map.of("type",List.of("string","null")));
        for (String key : List.of("addedConditions","removedConditions","addedInventory","removedInventory"))
            turnProperties.put(key, strings());
        var turn = new LinkedHashMap<String,Object>(object(turnProperties));
        turn.put("type", List.of("object","null"));
        var change = new LinkedHashMap<String,Object>();
        change.put("key",text());
        change.put("kind",enumeration(SceneFacts.Kind.values()));
        change.put("value",text());
        change.put("certainty",enumeration(SceneFacts.Certainty.values()));
        change.put("source",enumeration(SceneFacts.Source.values()));
        change.put("evidence",text());
        change.put("previousValue",Map.of("type",List.of("string","null")));
        change.put("reason",enumeration(SceneFacts.Reason.values()));
        var result = new LinkedHashMap<String,Object>();
        result.put("approved",Map.of("type","boolean"));
        result.put("turn",turn);
        result.put("changes",array(object(change)));
        result.put("conflicts",strings());
        return object(result);
    }

    private static Map<String,Object> text() { return Map.of("type","string"); }
    private static Map<String,Object> strings() { return array(text()); }
    private static Map<String,Object> integers() { return array(Map.of("type","integer")); }
    private static Map<String,Object> array(Map<String,Object> items) { return Map.of("type","array","items",items); }
    private static Map<String,Object> enumeration(Enum<?>[] values) {
        return Map.of("type","string","enum",Arrays.stream(values).map(Enum::name).toList());
    }
    private static Map<String,Object> object(Map<String,Object> properties) {
        return Map.of("type","object","additionalProperties",false,"properties",properties,
                "required",new ArrayList<>(properties.keySet()));
    }
}
