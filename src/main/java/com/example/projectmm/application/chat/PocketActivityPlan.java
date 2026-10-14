package com.example.projectmm.application.chat;

import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;

public record PocketActivityPlan(List<Activity> activities, List<Long> leaveCharacterIds, String pauseReason, PlayerStatePatch playerState) {
    public static String politeFallback(String text) {
        var endings = Map.ofEntries(Map.entry("하였다","했어요"),Map.entry("했다","했어요"),Map.entry("었다","었어요"),
                Map.entry("았다","았어요"),Map.entry("였다","였어요"),Map.entry("됐다","됐어요"),Map.entry("갔다","갔어요"),
                Map.entry("왔다","왔어요"),Map.entry("잤다","잤어요"),Map.entry("봤다","봤어요"),Map.entry("한다","해요"));
        var ending = java.util.regex.Pattern.compile("(하였다|했다|었다|았다|였다|됐다|갔다|왔다|잤다|봤다|한다)(?=\\s*(?:[.!?。！？]|$|\\p{So}))");
        var past = java.util.regex.Pattern.compile("([가-힣])다(?=\\s*(?:[.!?。！？]|$|\\p{So}))");
        java.util.function.UnaryOperator<String> soften = part -> {
            String mapped = ending.matcher(part).replaceAll(m -> endings.get(m.group()));
            return past.matcher(mapped).replaceAll(m -> (m.group(1).charAt(0)-0xac00)%28==20 ? m.group(1)+"어요" : m.group());
        };
        var quotes = java.util.regex.Pattern.compile("\"[^\"\\n]*\"|“[^”\\n]*”|‘[^’\\n]*’|'[^'\\n]*'").matcher(text);
        var result = new StringBuilder(); int previous = 0;
        while (quotes.find()) {
            result.append(soften.apply(text.substring(previous,quotes.start())));
            result.append(quotes.group());previous=quotes.end();
        }
        return result.append(soften.apply(text.substring(previous))).toString();
    }
    public enum Kind {
        WALK("🚶"), TALK("🗣️"), REST("🛋️"), WAIT("⏳"), SLEEP("💤"), READ("📖"),
        WORK("📝"), MEAL("🍽️"), COFFEE("☕"), COOK("🍳"), CARE("🩺"), TRAVEL("🚗"), RUN("🏃"), DRINK("🥤");
        private final String emoji;
        Kind(String emoji) { this.emoji = emoji; }
        public String emoji() { return emoji; }
    }
    public record PlayerStatePatch(String location, List<String> conditions, List<String> inventory) {}
    public record ActorCue(String actorId, List<String> emojis, String emotion, Integer turn, String pose) {
        public ActorCue(String actorId,List<String> emojis,String emotion,Integer turn) { this(actorId,emojis,emotion,turn,"LIGHT"); }
        public ActorCue { pose=Set.of("ARMS_CROSSED","DEPART","ARRIVE","WAVE").contains(Objects.toString(pose,"")) ? pose : "LIGHT"; }
    }
    public record Activity(LocalDateTime start, LocalDateTime end, Kind kind,
            List<Long> characterIds, String location, String summary, Boolean playerInvolved, List<ActorCue> actorCues,
            String interaction, List<String> interactionActorIds, String brief) {
        public Activity(LocalDateTime start,LocalDateTime end,Kind kind,List<Long> characterIds,String location,
                String summary,Boolean playerInvolved,List<ActorCue> actorCues,String interaction,List<String> interactionActorIds) {
            this(start,end,kind,characterIds,location,summary,playerInvolved,actorCues,interaction,interactionActorIds,null);
        }
        public Activity(LocalDateTime start,LocalDateTime end,Kind kind,List<Long> characterIds,String location,
                String summary,Boolean playerInvolved,List<ActorCue> actorCues) {
            this(start,end,kind,characterIds,location,summary,playerInvolved,actorCues,"NONE",List.of());
        }
        public Activity {
            brief = brief == null || brief.isBlank() || brief.length() > 180 ? null : brief.trim();
            // Pre-autonomy reports have no participation flag; explicit null is also legacy data.
            playerInvolved = Boolean.TRUE.equals(playerInvolved);
            actorCues = actorCues == null ? List.of() : List.copyOf(actorCues);
            interaction = Set.of("CLOSENESS","AFFECTION","INTIMACY").contains(Objects.toString(interaction,"")) ? interaction : "NONE";
            interactionActorIds = interactionActorIds == null ? List.of() : List.copyOf(interactionActorIds);
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
            LocalDateTime start, end;
            try {
                start = LocalDateTime.parse(item.path("start").asText());
                end = LocalDateTime.parse(item.path("end").asText());
            } catch (java.time.format.DateTimeParseException malformed) {
                // The server's claimed interval is authoritative. Do not guess a broken date;
                // use the existing ordered interval repair after validating all other fields.
                start = from; end = to; timingValid = false;
            }
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
            var participants=new LinkedHashSet<String>();ids.forEach(id->participants.add(id.toString()));
            if(playerInvolved) participants.add("player");
            var pair=new ArrayList<String>();
            if(item.path("interactionActorIds").isArray()) for(var actor:item.path("interactionActorIds")) {
                String actorId=actor.asText("");
                if(participants.contains(actorId) && !pair.contains(actorId)) pair.add(actorId);
            }
            if(!item.path("interactionActorIds").isArray() && participants.size()==2) pair.addAll(participants);
            String interaction=pair.size()==2 ? item.path("interaction").asText("NONE") : "NONE";
            result.add(new Activity(start, end, Kind.valueOf(item.path("kind").asText()),
                    List.copyOf(ids), location, summary, playerInvolved, correctArrivalCues(summary,actorCues(item,ids,playerInvolved),context),
                    interaction,"NONE".equals(interaction) ? List.of() : pair, item.path("brief").asText(null)));
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
                        activity.characterIds(), activity.location(), activity.summary(), activity.playerInvolved(), activity.actorCues(),
                        activity.interaction(), activity.interactionActorIds(), activity.brief()));
            }
            result = corrected;
        }
        // Absence narration cannot change the user's cast selection. Going out is a story
        // event, not a system departure; legacy AI departure metadata is ignored entirely.
        var leaves = new ArrayList<Long>();
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
    static List<ActorCue> correctArrivalCues(String summary, List<ActorCue> cues, WorldRoomDialogueContext context) {
        var names=new LinkedHashMap<String,String>();
        context.roomCast().forEach(c -> names.put(c.characterId().toString(),c.characterName()));
        if(context.playerProfile()!=null) names.put("player",context.playerProfile().name());
        return cues.stream().map(c -> {
            String name=names.get(c.actorId());
            String pose=arrivalPose(summary,name,c.pose());
            return new ActorCue(c.actorId(),c.emojis(),c.emotion(),c.turn(),pose);
        }).toList();
    }
    static String arrivalPose(String summary, String name, String pose) {
        if(!"DEPART".equals(pose) || name==null || name.isBlank() || summary==null) return pose;
        // Only correct an explicit subject's completed return, never "sent time" or another person's action.
        var subject=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(name)+"(?:은|는|이|가)\\s+").matcher(summary);
        if(!subject.find()) return pose;
        String clause=summary.substring(subject.end()).split("[,;.!?\\n]",2)[0];
        var nextSubject=java.util.regex.Pattern.compile("[가-힣A-Za-z]+(?:은|는|이|가)\\s+").matcher(clause);
        if(nextSubject.find()) clause=clause.substring(0,nextSubject.start());
        var arrival=java.util.regex.Pattern.compile("(?:돌아왔|귀가했|귀가하였|도착했|도착하였)(?:고|다|으며|어요|습니다)").matcher(clause);
        if(!arrival.find()) return pose;
        String after=clause.substring(arrival.end());
        if(after.matches("(?s).*(?:외출했|떠났|나갔|출발했).*")) return pose;
        return "ARRIVE";
    }
    private static List<ActorCue> actorCues(tools.jackson.databind.JsonNode item, List<Long> ids, boolean player) {
        var allowed = new HashSet<String>(); ids.forEach(id -> allowed.add(id.toString()));
        if (player) allowed.add("player");
        var cues = new ArrayList<ActorCue>(); var seen = new HashSet<String>();
        var visuals = item.path("actorCues");
        if (!visuals.isArray()) return List.of(); // Optional presentation must not block story progress.
        for (var cue : visuals) {
            String actor = cue.path("actorId").asText("");
            if (!allowed.contains(actor) || !seen.add(actor) || cues.size() >= 9) continue;
            var emojis = new ArrayList<String>();
            if (cue.path("emojis").isArray()) for (var emoji : cue.path("emojis")) {
                if (emoji.isString() && !emoji.asText().isBlank() && emoji.asText().length() <= 24 && emojis.size() < 2)
                    emojis.add(emoji.asText());
            }
            String emotion = cue.path("emotion").asText("CALM").toUpperCase(Locale.ROOT);
            if (!Set.of("CALM","ANGRY","HAPPY","SAD").contains(emotion)) emotion = "CALM";
            int turn = cue.path("turn").asInt(cues.size());
            cues.add(new ActorCue(actor,List.copyOf(emojis),emotion,Math.max(0,Math.min(8,turn)),cue.path("pose").asText("LIGHT")));
        }
        return List.copyOf(cues);
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
