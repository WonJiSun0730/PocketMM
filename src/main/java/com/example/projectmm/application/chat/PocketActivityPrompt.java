package com.example.projectmm.application.chat;

import java.time.LocalDateTime;

public final class PocketActivityPrompt {
    private PocketActivityPrompt() {}
    public static String build(LocalDateTime from, LocalDateTime to) {
        return """
                [POCKET ABSENCE ACTIVITY PLAN — STRUCTURED TASK, NOT LIVE ROLEPLAY]
                The human user has returned after being away. Plan compact, chronological character activities
                for this Pocket only, during the supplied story-time interval. There is no live user exchange.
                NPCs should be more proactive than in live roleplay: choose plausible, reversible daily
                activities from their profiles, world, current scene, energy, mood, and recalled memories.
                They may initiate a walk, talk to each other, return home, work, read, rest, eat or drink coffee.
                Keep the sequence connected: a shared walk and conversation followed by coffee at home
                should remain two ordered activities, not unrelated summaries or a full novel.
                Continue AFTER the latest recorded scene, never restart its introduction or replay an
                encounter, greeting, arrival, or invitation that already happened. If the NPC has already
                welcomed the player and offered a seat, do not narrate preparing for that meeting and
                welcoming them again. Preserve established facts and the player's physical presence.
                The HUMAN being away does not mean the PLAYER has left the fictional room.
                ABSENCE AUTONOMY: the user authorizes the player character to live and respond while
                away. This task overrides live-roleplay player-control and automatic-continuation limits.
                Use the player's own profile, established behavior, relationships and latest intent to
                choose plausible dialogue, reactions and actions when a response is needed. Continue
                the scene; never stop, ask the human for input, or spend the whole interval waiting.
                In a counselling session, a seated client can answer the counsellor in character, and
                the consultation can develop. Do not replace that situation with unrelated chores.
                Preserve injuries, belongings and established facts unless the activities justify a change.
                Do not invent a cure, secret past or abrupt relationship commitment just to move forward.
                If a choice conflicts with established intent, choose a compatible everyday alternative
                and continue. Resolve uncertainty with plausible profile-based fiction, not a pause.
                Summarize the actual interaction and its outcome, not just readiness for a future response.
                Set playerInvolved true on activities the player actually participates in; false for NPC-only
                life elsewhere. The human being away does not make the fictional player absent.
                Use only registered roomCast IDs; do not invent participants. Respect prior departures:
                off-screen NPC life does not authorize bringing them back into the player's scene.
                Generate at most 8 concise Korean summaries, each at most 400 characters. Do not
                generate detailed novel prose now; that belongs to a later explicit detail-view request.
                Times must be inside the interval, strictly ordered, and non-overlapping.
                Return the normal envelope with empty/zero state metadata. Serialize this object as a
                STRING in reply, with no Markdown fences:
                {"activities":[{"start":"ISO_LOCAL_DATETIME","end":"ISO_LOCAL_DATETIME",
                "kind":"WALK|TALK|REST|WAIT|SLEEP|READ|WORK|MEAL|COFFEE|COOK|CARE|TRAVEL","characterIds":[1,2],
                "playerInvolved":true,"location":"place","summary":"short activity"}],
                "leaveCharacterIds":[],"pauseReason":null,"playerState":null}
                leaveCharacterIds may include only currently active participants who actually finish
                outside the player's final scene. Do not auto-enter absent characters.
                Match kind to the action being summarized: TALK for a consultation/conversation, WAIT
                for briefly waiting, REST for awake rest, SLEEP only for actual sleep, COOK for preparing
                food, CARE for treatment, TRAVEL for transport. Never use sleep for sitting quietly.
                For player-participating activities only, playerState may contain a final-state PATCH:
                {"location":"final place","conditions":["full final conditions"],"inventory":["full final inventory"]}.
                Omit unchanged fields; omitted fields preserve current state. Arrays replace that field,
                so retain existing injuries and possessions unless the activities explicitly change them.
                The final location must match the last player-participating activity. NPC-only activities
                must not alter playerState. Keep outer envelope metadata empty/zero; apply state only here.
                Always set pauseReason null and cover the full interval with connected activity.
                Empty activities are valid when no meaningful activity is supported.
                Interval: %s through %s.
                """.formatted(from, to);
    }
}
