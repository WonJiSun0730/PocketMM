package com.example.projectmm.application.chat;

import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

public record PocketActivityPlan(List<Activity> activities, List<Long> leaveCharacterIds, String pauseReason, PlayerStatePatch playerState) {
    public enum Kind {
        WALK("🚶"), TALK("🗣️"), REST("🛋️"), WAIT("⏳"), SLEEP("💤"), READ("📖"),
        WORK("📝"), MEAL("🍽️"), COFFEE("☕"), COOK("🍳"), CARE("🩺"), TRAVEL("🚗");
        private final String emoji;
        Kind(String emoji) { this.emoji = emoji; }
        public String emoji() { return emoji; }
    }
    public record PlayerStatePatch(String location, List<String> conditions, List<String> inventory) {}
    public record Activity(LocalDateTime start, LocalDateTime end, Kind kind,
            List<Long> characterIds, String location, String summary, Boolean playerInvolved) {
        public Activity {
            // Pre-autonomy reports have no participation flag; explicit null is also legacy data.
            playerInvolved = Boolean.TRUE.equals(playerInvolved);
        }
    }
    public static PocketActivityPlan parse(String json, WorldRoomDialogueContext context,
            LocalDateTime from, LocalDateTime to) {
        var node = new ObjectMapper().readTree(json);
        if (!node.isObject() || !node.path("activities").isArray() || node.path("activities").size() > 8)
            throw new IllegalArgumentException("Invalid activity plan");
        var allowed = new HashSet<Long>();
        context.roomCast().forEach(c -> allowed.add(c.characterId()));
        var involved = new HashSet<Long>();
        var result = new ArrayList<Activity>();
        if (!to.isAfter(from)) throw new IllegalArgumentException("Invalid absence interval");
        boolean timingValid = true;
        var previous = from;
        for (var item : node.path("activities")) {
            var start = LocalDateTime.parse(item.path("start").asText());
            var end = LocalDateTime.parse(item.path("end").asText());
            if (start.isBefore(previous) || !end.isAfter(start) || end.isAfter(to)) timingValid = false;
            var ids = new ArrayList<Long>();
            if (!item.path("characterIds").isArray()) throw new IllegalArgumentException("Missing participants");
            for (var id : item.path("characterIds")) {
                if (!id.isIntegralNumber() || !allowed.contains(id.asLong()) || ids.contains(id.asLong()))
                    throw new IllegalArgumentException("Unregistered or duplicate participant");
                ids.add(id.asLong());
            }
            boolean playerInvolved = item.path("playerInvolved").asBoolean(false);
            if (ids.isEmpty() && !playerInvolved) throw new IllegalArgumentException("Activity requires participants");
            var summary = item.path("summary").asText("").trim();
            var location = item.path("location").asText("").trim();
            if (summary.isBlank() || summary.length() > 400 || location.length() > 200)
                throw new IllegalArgumentException("Invalid activity text");
            result.add(new Activity(start, end, Kind.valueOf(item.path("kind").asText()),
                    List.copyOf(ids), location, summary, playerInvolved));
            involved.addAll(ids); previous = end;
        }
        if (!timingValid) {
            // Preserve the proposed story order. Repair only invalid model timestamps; never
            // extend the simulation past the requested interval or discard an activity's actors.
            long intervalNanos = java.time.Duration.between(from,to).toNanos();
            var corrected = new ArrayList<Activity>();
            for (int i = 0; i < result.size(); i++) {
                var activity = result.get(i);
                corrected.add(new Activity(from.plusNanos(intervalNanos * i / result.size()),
                        from.plusNanos(intervalNanos * (i + 1) / result.size()), activity.kind(),
                        activity.characterIds(), activity.location(), activity.summary(), activity.playerInvolved()));
            }
            result = corrected;
        }
        if (!node.path("leaveCharacterIds").isArray()) throw new IllegalArgumentException("Missing scene departures");
        var active = context.activeCast().stream().map(c -> c.characterId()).collect(java.util.stream.Collectors.toSet());
        var leaves = new ArrayList<Long>();
        for (var id : node.path("leaveCharacterIds")) {
            if (!id.isIntegralNumber() || !active.contains(id.asLong()) || leaves.contains(id.asLong()))
                throw new IllegalArgumentException("Invalid scene departure");
            leaves.add(id.asLong());
        }
        // Older model outputs must not leave autonomous simulation permanently waiting for a user.
        PlayerStatePatch patch = null;
        var lastPlayerActivity = result.stream().filter(Activity::playerInvolved).reduce((x,y) -> y).orElse(null);
        var state = node.path("playerState");
        if (!state.isMissingNode() && !state.isNull()) {
            if (!state.isObject()) throw new IllegalArgumentException("Invalid player state patch");
            if (lastPlayerActivity == null) throw new IllegalArgumentException("Player state requires player participation");
            // The activity timeline is authoritative. The model may use a different label
            // in its redundant final-state location (e.g. centre vs centre counselling room).
            // Never reject the whole plan or teleport the player based on that duplicate field.
            patch = new PlayerStatePatch(null, stateList(state,"conditions"), stateList(state,"inventory"));
        }
        // Participation already grounds the final place even if the model omitted its state patch.
        if (lastPlayerActivity != null && !lastPlayerActivity.location().isBlank()) {
            patch = new PlayerStatePatch(lastPlayerActivity.location(), patch == null ? null : patch.conditions(),
                    patch == null ? null : patch.inventory());
        }
        return new PocketActivityPlan(List.copyOf(result), List.copyOf(leaves), null, patch);
    }
    private static List<String> stateList(tools.jackson.databind.JsonNode state, String name) {
        if (!state.has(name)) return null;
        var items = state.path(name);
        if (!items.isArray() || items.size() > 50) throw new IllegalArgumentException("Invalid player state list");
        var values = new ArrayList<String>();
        for (var item : items) {
            if (!item.isString() || item.asText().isBlank() || item.asText().trim().length() > 300)
                throw new IllegalArgumentException("Invalid player state item");
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }
    public LocalDateTime appliedUntil(LocalDateTime from, LocalDateTime to) { return to; }
}
