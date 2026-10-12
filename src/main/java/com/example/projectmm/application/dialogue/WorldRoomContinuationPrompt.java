package com.example.projectmm.application.dialogue;

public final class WorldRoomContinuationPrompt {
    private WorldRoomContinuationPrompt() {}

    public static String build(boolean noControlPlayer) {
        return build(noControlPlayer, 1);
    }

    public static String build(boolean noControlPlayer, int consecutiveNo) {
        if (consecutiveNo < 1) throw new IllegalArgumentException("Continuation number must be positive");
        String scope = noControlPlayer ? "STRICT" : consecutiveNo <= 2 ? "LOW"
                : consecutiveNo <= 5 ? "MODERATE" : "HIGH";
        String header = "[Current automatic progression directive: consecutiveNo=" + consecutiveNo
                + "; playerScope=" + scope + "]\n";
        if (noControlPlayer) return header + """
                [Continue the current scene naturally for one developed story beat without controlling the player character.
                Advance only NPC actions, dialogue, environment, and context-supported events.
                Do not invent any player dialogue, action, thought, feeling, reaction, decision, or movement.
                Write vivid, connected prose and leave room for the user's response.]
                """;
        if (consecutiveNo <= 2) return header + """
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
        String delegated = consecutiveNo <= 5 ? """
                Player scope MODERATE (consecutive automatic continuations 3-5):
                - Slightly increase participation within the current user-established task.
                  You may supply one brief, context-supported player utterance or a minor reaction,
                  but neither is mandatory. Do not fabricate a new opinion, goal, or knowledge.
                - Do not make the player initiate an unrelated exchange or supply repeated answers
                  to NPC questions. Leave proposals needing a real choice unresolved.
                """ : """
                Player scope HIGH (consecutive automatic continuations 6+):
                - The user delegates ordinary player dialogue and reversible actions within the
                  established scene and characterization. You may portray the player speaking,
                  responding, and taking routine next steps; keep the exchange bounded to one beat.
                - Do not fabricate new background knowledge, independent goals, relationship
                  commitments, intimacy, dangerous choices, or irreversible outcomes.
                - Do not exhaust the scene with a chain of questions, answers, and completed choices.
                """;
        return header + """
                [Automatically continue the current scene for one developed story beat.
                Apply only the current playerScope below; prior AI-authored behavior is not permission
                to increase it. Fill concrete procedural and sensory details, advance NPCs and the
                environment, and do not freeze the scene or request every routine detail.
                """ + delegated + """
                - Every reply must leave a genuine opening for the player's next action or answer,
                  even at HIGH scope. End before the next consequential choice; a pause, NPC question,
                  unfinished interaction, or new circumstance can provide that opening naturally.
                - Do not decide the player's next response in the closing sentence. Do not force a
                  generic choice menu or repetitive 'What will you do?' ending.
                - Explicit user limits take precedence. Keep absent characters absent and do not
                  force speech in solitary or sleeping scenes. Preserve authoritative scene state.]
                """;
    }
}
