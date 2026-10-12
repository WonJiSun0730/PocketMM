package com.example.projectmm.application.chat;

import java.time.LocalDateTime;

public final class PocketActivityPrompt {
    private PocketActivityPrompt() {}
    public static String build(LocalDateTime from, LocalDateTime to) {
        return """
                [POCKET ABSENCE ACTIVITY PLAN — STRUCTURED TASK, NOT LIVE ROLEPLAY]
                The human user has returned after being away. Plan compact, chronological NPC activities
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
                NPCs may do quiet daily tasks while waiting; do not invent new exchanges with the player.
                If further interaction needs the player's answer or action, pause rather than invent it.
                No player dialogue, actions, decisions, possessions, conditions, or location changes.
                No major relationship commitments, dangerous or irreversible choices, invented diagnoses,
                private memories, or forced encounters with the player. Pause before such a choice and
                give a short Korean pauseReason. Departure from the player's scene must be explicit.
                Use only registered roomCast IDs; do not invent participants. Respect prior departures:
                off-screen NPC life does not authorize bringing them back into the player's scene.
                Generate at most 8 concise Korean summaries, each at most 400 characters. Do not
                generate detailed novel prose now; that belongs to a later explicit detail-view request.
                Times must be inside the interval, strictly ordered, and non-overlapping.
                Return the normal envelope with empty/zero state metadata. Serialize this object as a
                STRING in reply, with no Markdown fences:
                {"activities":[{"start":"ISO_LOCAL_DATETIME","end":"ISO_LOCAL_DATETIME",
                "kind":"WALK|TALK|REST|READ|WORK|MEAL|COFFEE","characterIds":[1,2],
                "location":"place","summary":"short activity"}],
                "leaveCharacterIds":[],"pauseReason":null}
                leaveCharacterIds may include only currently active participants who actually finish
                outside the player's scene. Do not move the player or auto-enter absent characters.
                Empty activities are valid when no meaningful activity is supported.
                Interval: %s through %s.
                """.formatted(from, to);
    }
}
