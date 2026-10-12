package com.example.projectmm.application.dialogue;

public final class WorldRoomContinuationPrompt {
    private WorldRoomContinuationPrompt() {}

    public static String build(boolean noControlPlayer) {
        if (noControlPlayer) return """
                [Continue the current scene naturally for one developed story beat without controlling the player character.
                Advance only NPC actions, dialogue, environment, and context-supported events.
                Do not invent any player dialogue, action, thought, feeling, reaction, decision, or movement.
                Write vivid, connected prose and leave room for the user's response.]
                """;
        return """
                [Automatically continue the current scene for one developed story beat.
                Elaborate only the concrete steps of player actions already established by the user,
                within their stated stage and scope. Fill routine procedural details without asking
                the user to specify every small step. Advance NPCs, environment, and direct consequences
                with vivid, connected prose; do not freeze the scene or merely repeat the prior input.
                Automatic continuation is not permission to choose the player's next response.
                Never invent new player dialogue, intent, opinion, knowledge, emotion, agreement,
                refusal, or independent next objective. Even a nod or a short answer may decide a choice.
                Honor an explicit user delegation only within its stated scope, not from the profile
                or previous AI-authored player behavior. Stop before a fresh player answer or decision
                is needed; do not complete repeated NPC-question/player-answer loops.
                Keep absent characters absent and do not force speech in a solitary or sleeping scene.]
                """;
    }
}
