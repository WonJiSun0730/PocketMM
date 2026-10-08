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
                Write the player as an active participant, including natural spoken contributions and
                replies in a conversational scene, actions, everyday choices, and context-supported
                thoughts and feelings consistent with their PlayerProfile and established portrayal.
                The user delegates ordinary scene progression; do not wait for a choice at every step
                or reduce the player to a brief reply while only NPCs talk. Use connected narrative
                paragraphs and a meaningful exchange, with sensory detail and character-specific behavior.
                Preserve the user's stated intent and keep high-stakes commitments, major relationship
                changes, intimacy, dangerous acts, and irreversible outcomes open for the user.
                Keep absent characters absent and do not force speech in a solitary or sleeping scene.]
                """;
    }
}
