package com.example.projectmm.application.chat;

import java.time.LocalDateTime;

public final class PocketActivityPrompt {
    private PocketActivityPrompt() {}
    public static String build(LocalDateTime from, LocalDateTime to) {
        long minutes=java.time.Duration.between(from,to).toMinutes();
        int activityBudget=(int)Math.max(1,Math.min(8,minutes/15));
        return """
                [POCKET ABSENCE ACTIVITY PLAN — STRUCTURED TASK, NOT LIVE ROLEPLAY]
                Every activity must include a separate "brief" field: one short, warm Korean sentence
                naming the participants and the actual action, using ~했어요 / ~했답니다.
                Preserve the core outcome (a kiss, a meal's menu, a relationship realization) without
                inventing one. A gentle emoticon or action emoji is welcome, but avoid baby talk.
                Example: "밀리와 마르바스는 산책하다가 뽀뽀했어요! ^^=3❤"
                Keep "summary" as the complete factual timeline; NEVER replace it with brief.
                brief is display-only and MUST NOT determine memory or state changes.
                The human user has returned after being away. Plan compact, chronological character activities
                for this Pocket only, during the supplied story-time interval. There is no live user exchange.
                NPCs should be more proactive than in live roleplay: choose plausible, reversible daily
                activities from their profiles, world, current scene, energy, mood, and recalled memories.
                CONTINUING WITHOUT A NEW EVENT: if the character is still sleeping, resting,
                reading, working or traveling and nothing new occurred, do not invent a scene
                change or retell starting/finishing that activity. Use a short present-progress
                brief such as "밀리와 마르바스는 아직 자고 있어요. 💤" or "밀리는 아직 책을 읽고 있어요. 📖".
                Preserve the factual ongoing phase in summary; do not manufacture a new result.
                DAILY RESPONSIBILITIES: read each NPC's occupation, profile and established schedule.
                Co-habitation is not permanent leisure or continuous co-location. On a plausible
                working weekday, an employed NPC may prepare for work, commute, work and return,
                leaving the player to their own activities. Respect established days off, shift
                hours, appointments, remote work, holidays and world-specific calendar rules.
                If work hours or days off are unspecified, improvise a plausible provisional
                schedule from occupation, era, local culture and the current scene. Different jobs
                need not all follow 9–5. A counselor may have appointments or a suitable day shift.
                Treat inferred hours and holidays as flexible daily assumptions, not immutable lore.
                Later player directions, explicit schedules and corrections always override these
                improvised assumptions and any memories describing that previous day's routine.
                Remember what actually happened, never turn one day's inferred hours into a rule
                that takes priority over the player's subsequent play policy.
                Account for routine obligations even without a dramatic new event; do not keep
                a working NPC at home merely because they currently share a home with the player.
                They may initiate a walk, talk to each other, return home, work, read, rest, eat or drink coffee.
                Keep the sequence connected: a shared walk and conversation followed by coffee at home
                should remain two ordered activities only when enough time is available. In a short
                interval, continue just the current phase rather than rushing through the whole sequence.
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
                SLOW EVERYDAY PACING: ordinarily spend 15-20 minutes on one meaningful activity.
                Do not create a new event each minute or accelerate routine life to fill summaries.
                One event may last much longer (sleep, work, recovery, reading). Minor gestures and
                conversation replies belong inside that same activity, not separate incidents.
                Do not pack a rapid chain of distinct decisions into one summary to bypass pacing.
                Prefer fewer activities or an empty list when nothing meaningful happens.
                Do not force an appointment to end or a new incident to begin just because time passed.
                Generate at most %d concise Korean summaries, each at most 400 characters. Do not
                generate detailed novel prose now; that belongs to a later explicit detail-view request.
                Times must be inside the interval, strictly ordered, and non-overlapping.
                Copy the exact supplied boundary times including seconds/fractions. A rounded minute
                before the supplied start is outside the interval. Cover the full interval with subsequent
                activities, rather than declaring three hours elapsed after only thirty minutes of events.
                Return the normal envelope with empty/zero state metadata. Serialize this object as a
                STRING in reply, with no Markdown fences:
                {"activities":[{"start":"ISO_LOCAL_DATETIME","end":"ISO_LOCAL_DATETIME",
                "kind":"WALK|RUN|TALK|REST|WAIT|SLEEP|READ|WORK|MEAL|DRINK|COFFEE|COOK|CARE|TRAVEL","characterIds":[1,2],
                "playerInvolved":true,"location":"place","summary":"short activity",
                "actorCues":[{"actorId":"player","emojis":["🤔","💤"],"emotion":"CALM","turn":0},
                {"actorId":"1","emojis":["🛏️","✔️"],"emotion":"CALM","turn":1}]}],
                "leaveCharacterIds":[],"pauseReason":null,"playerState":null}
                leaveCharacterIds MUST always be []. The user's cast selection and active status
                are controlled only by explicit user operations, never by absence narration.
                Going out, commuting, working elsewhere, separation or returning home are in-story
                events only. Describe their locations and actual participants without system
                departures, automatic entries, or modifying the cast selection.
                Match kind to the action being summarized: TALK for a consultation/conversation, WAIT
                for briefly waiting, REST for awake rest, SLEEP only for actual sleep, COOK for preparing
                food, CARE for treatment, TRAVEL for transport. Never use sleep for sitting quietly.
                For player-participating activities only, playerState may contain a final-state PATCH:
                {"location":"final place","conditions":["full final conditions"],"inventory":["full final inventory"]}.
                Omit unchanged fields; omitted fields preserve current state. Arrays replace that field,
                so retain existing injuries and possessions unless the activities explicitly change them.
                The final location must match the last player-participating activity. NPC-only activities
                must not alter playerState. Keep outer envelope metadata empty/zero; apply state only here.
                MEAL DETAIL: name the actual food briefly in the summary, not just "ate a simple meal".
                Example: "밀리는 샐러드로 간단하게 식사를 때웠다." Give that actor exactly ONE matching
                food emoji, e.g. salad 🥗, noodles 🍜, rice 🍚, sandwich 🥪. Never add a second emoji
                or a mood emoji for MEAL. If no suitable food emoji exists, use only 🍽️.
                Choose a plausible menu consistent with the place, profile and established food facts.
                Do not invent elaborate cooking or food changes merely to provide a visual icon.
                For each participating actor, provide actorCues with actorId "player" or the NPC ID as
                a string. Choose ONE topic-specific emoji where possible, at most TWO. A discussion of
                sleep can use thoughtful/sleep icons for the client and bed/check icons for the counsellor;
                do not use the same generic speaking icon for everyone. Emojis contain icons only, no text.
                emotion is CALM, ANGRY, HAPPY or SAD, grounded in that actor's mood in this activity.
                Optional pose is LIGHT (lively relaxed standing) or ARMS_CROSSED (folded arms when
                appropriate to that actor and moment). Vary gestures naturally, never fold arms while
                eating, drinking, running or asleep; do not make everyone use the same pose by default.
                For actual outings/returns only, pose may be DEPART for the departing actor, ARRIVE for the returning/meeting actor, WAVE for the actor seeing them off or welcoming them. The subject of seeing off/sending someone is the waver, not the departing actor. Include only actual involved participants; never invent an outing. These poses are replay visuals only and never change cast membership.
                turn is the relative speaking order (0 first); alternating speakers will be animated.
                VISUAL INTERACTION: optional interaction is NONE, CLOSENESS (a moment of growing
                familiarity), AFFECTION (romantic kissing, affectionate embrace or romantic touch),
                or INTIMACY (consensual intimacy between characters explicitly established as 19 or older).
                interactionActorIds names exactly the two participants using "player" or NPC ID strings.
                Ordinary friendly/family hugs are not romantic AFFECTION. Never invent intimacy just
                to trigger an animation; require established mutual affection and intent. If ages are
                missing, under 19 or consent unclear, do not use INTIMACY. Describe adult intimacy only
                as sharing a private affectionate moment; no explicit sexual acts or anatomical detail.
                RUN is actual running, DRINK is drinking (COFFEE for coffee); MEAL is eating.
                SLEEP actors use 💤. Keep these cues scoped to this activity, not a permanent trait.
                Always set pauseReason null and cover the full interval with connected activity.
                Empty activities are valid when no meaningful activity is supported.
                Interval: %s through %s.
                """.formatted(activityBudget, from, to);
    }
}
