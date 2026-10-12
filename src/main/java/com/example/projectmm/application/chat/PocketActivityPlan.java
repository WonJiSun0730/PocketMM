package com.example.projectmm.application.chat;

import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

public record PocketActivityPlan(List<Activity> activities, List<Long> leaveCharacterIds, String pauseReason) {
    public enum Kind { WALK, TALK, REST, READ, WORK, MEAL, COFFEE }
    public record Activity(LocalDateTime start, LocalDateTime end, Kind kind,
            List<Long> characterIds, String location, String summary) {}
    public static PocketActivityPlan parse(String json, WorldRoomDialogueContext context,
            LocalDateTime from, LocalDateTime to) {
        var node = new ObjectMapper().readTree(json);
        if (!node.isObject() || !node.path("activities").isArray() || node.path("activities").size() > 8)
            throw new IllegalArgumentException("Invalid activity plan");
        var allowed = new HashSet<Long>();
        context.roomCast().forEach(c -> allowed.add(c.characterId()));
        var involved = new HashSet<Long>();
        var result = new ArrayList<Activity>();
        var previous = from;
        for (var item : node.path("activities")) {
            var start = LocalDateTime.parse(item.path("start").asText());
            var end = LocalDateTime.parse(item.path("end").asText());
            if (start.isBefore(previous) || !end.isAfter(start) || end.isAfter(to))
                throw new IllegalArgumentException("Activity outside interval or overlapping");
            var ids = new ArrayList<Long>();
            if (!item.path("characterIds").isArray()) throw new IllegalArgumentException("Missing participants");
            for (var id : item.path("characterIds")) {
                if (!id.isIntegralNumber() || !allowed.contains(id.asLong()) || ids.contains(id.asLong()))
                    throw new IllegalArgumentException("Unregistered or duplicate participant");
                ids.add(id.asLong());
            }
            if (ids.isEmpty()) throw new IllegalArgumentException("Activity requires NPC participants");
            var summary = item.path("summary").asText("").trim();
            var location = item.path("location").asText("").trim();
            if (summary.isBlank() || summary.length() > 400 || location.length() > 200)
                throw new IllegalArgumentException("Invalid activity text");
            result.add(new Activity(start, end, Kind.valueOf(item.path("kind").asText()),
                    List.copyOf(ids), location, summary));
            involved.addAll(ids); previous = end;
        }
        if (!node.path("leaveCharacterIds").isArray()) throw new IllegalArgumentException("Missing scene departures");
        var active = context.activeCast().stream().map(c -> c.characterId()).collect(java.util.stream.Collectors.toSet());
        var leaves = new ArrayList<Long>();
        for (var id : node.path("leaveCharacterIds")) {
            if (!id.isIntegralNumber() || !active.contains(id.asLong()) || !involved.contains(id.asLong()) || leaves.contains(id.asLong()))
                throw new IllegalArgumentException("Invalid scene departure");
            leaves.add(id.asLong());
        }
        String pause = node.path("pauseReason").asText("").trim();
        if (pause.length() > 300) throw new IllegalArgumentException("Invalid pause reason");
        return new PocketActivityPlan(List.copyOf(result), List.copyOf(leaves), pause.isEmpty() ? null : pause);
    }
    public LocalDateTime appliedUntil(LocalDateTime from, LocalDateTime to) {
        return pauseReason == null ? to : activities.isEmpty() ? from : activities.getLast().end();
    }
}
