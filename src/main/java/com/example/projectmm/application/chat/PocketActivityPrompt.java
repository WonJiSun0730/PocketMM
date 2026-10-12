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
                PRIMARY VIEWPOINT: continue the player character's own life from their current location
                and latest actions. Room membership or an activeCast ID does not prove co-location.
                NPC life elsewhere may be a short secondary activity, never replace the player's thread.
                Permit solo player activities with characterIds:[] and playerInvolved:true. Only include
                an NPC ID if that NPC actually participates; a counsellor need not follow a client home.
                COMPLETED EVENTS STAY COMPLETED: read the latest conversation and absence outcomes.
                Do not repeat previously answered questions, advice, greetings, or an already finished
                consultation/meal/work task. "Finished", "left", "returned home", and "see you next time"
                are phase boundaries. Continue the next plausible phase, not that completed interaction.
                Let appointments, meals and shifts end after plausible durations. A multi-hour interval
                should include subsequent personal life where appropriate, not stretching one exchange
                or repeating the same summary to keep all registered characters together.
                Examples (adapt to profiles, do not impose them): after a consultation ends, the client
                leaves and continues their own evening; after a shared meal ends, people may part;
                after a shift ends, return home; after arriving home, unpack, rest or follow personal plans.
                Preserve an explicitly ongoing interaction long enough for new developments, then allow
                a natural conclusion. Never force homecoming or togetherness against the latest intent.
                Preserve injuries, belongings and established facts unless the activities justify a change.
                Do not invent a cure, secret past or abrupt relationship commitment just to move forward.
                If a choice conflicts with established intent, choose a compatible everyday alternative
                and continue. Resolve uncertainty with plausible profile-based fiction, not a pause.
                Summarize the actual interaction and its outcome, not just readiness for a future response.
                Set playerInvolved true on activities the player actually participates in; false for NPC-only
                life elsewhere. The human being away does not make the fictional player absent.
                Use only registered roomCast IDs for NPCs; the player needs no NPC ID. do not invent participants. Respect prior departures:
                off-screen NPC life does not authorize bringing them back into the player's scene.
                Generate at most 8 concise Korean summaries, each at most 400 characters. Do not
                generate detailed novel prose now; that belongs to a later explicit detail-view request.
                Times must be inside the interval, strictly ordered, and non-overlapping.
                Copy the exact supplied boundary times including seconds/fractions. A rounded minute
                before the supplied start is outside the interval. Cover the full interval with subsequent
                activities, rather than declaring three hours elapsed after only thirty minutes of events.
                Return the normal envelope with empty/zero state metadata. Serialize this object as a
                STRING in reply, with no Markdown fences:
                {"activities":[{"start":"ISO_LOCAL_DATETIME","end":"ISO_LOCAL_DATETIME",
                "kind":"WALK|TALK|REST|WAIT|SLEEP|READ|WORK|MEAL|COFFEE|COOK|CARE|TRAVEL","characterIds":[1,2],
                "playerInvolved":true,"location":"place","summary":"short activity",
                "actorCues":[{"actorId":"player","emojis":["🤔","💤"],"emotion":"CALM","turn":0},
                {"actorId":"1","emojis":["🛏️","✔️"],"emotion":"CALM","turn":1}]}],
                "leaveCharacterIds":[],"pauseReason":null,"playerState":null}
                leaveCharacterIds may include only currently active participants who actually finish
                outside the player's final scene. An active NPC who stays behind when the player leaves
                belongs in leaveCharacterIds even if not otherwise included in an activity. Departures
                track the player's scene, not whether that NPC stops living elsewhere. Do not auto-enter
                absent characters.
                Match kind to the action being summarized: TALK for a consultation/conversation, WAIT
                for briefly waiting, REST for awake rest, SLEEP only for actual sleep, COOK for preparing
                food, CARE for treatment, TRAVEL for transport. Never use sleep for sitting quietly.
                For player-participating activities only, playerState may contain a final-state PATCH:
                {"location":"final place","conditions":["full final conditions"],"inventory":["full final inventory"]}.
                Omit unchanged fields; omitted fields preserve current state. Arrays replace that field,
                so retain existing injuries and possessions unless the activities explicitly change them.
                The final location must match the last player-participating activity. NPC-only activities
                must not alter playerState. Keep outer envelope metadata empty/zero; apply state only here.
                For each participating actor, provide actorCues with actorId "player" or the NPC ID as
                a string. Choose ONE topic-specific emoji where possible, at most TWO. A discussion of
                sleep can use thoughtful/sleep icons for the client and bed/check icons for the counsellor;
                do not use the same generic speaking icon for everyone. Emojis contain icons only, no text.
                emotion is CALM, ANGRY, HAPPY or SAD, grounded in that actor's mood in this activity.
                turn is the relative speaking order (0 first); alternating speakers will be animated.
                SLEEP actors use 💤. Keep these cues scoped to this activity, not a permanent trait.
                Always set pauseReason null and cover the full interval with connected activity.
                Empty activities are valid when no meaningful activity is supported.
                Interval: %s through %s.
                """.formatted(from, to);
    }
}
