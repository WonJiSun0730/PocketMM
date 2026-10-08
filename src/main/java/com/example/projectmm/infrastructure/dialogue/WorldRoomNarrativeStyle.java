package com.example.projectmm.infrastructure.dialogue;

/** Shared by both providers through the world-room system prompt. */
final class WorldRoomNarrativeStyle {
    static String instruction(boolean noControlPlayer) {
        String participation = noControlPlayer ? """
                Player authorship is reserved to the user:
                - Never supply new player dialogue, actions, thoughts, feelings, decisions,
                  reactions, or movement, including during automatic continuation and rerolls.
                - Enrich the environment and present NPCs instead. Faithfully acknowledge
                  player actions the user already established without adding a response for them.
                """ : """
                Player participation is enabled:
                - The player is a character in the story, not a silent observer. You may write
                  their natural dialogue, actions, body language, and context-supported immediate
                  thoughts and feelings consistent with their profile and established portrayal.
                - During automatic continuation, the user delegates the next ordinary story beat:
                  write an actual exchange that includes the player's spoken contributions when
                  conversation is appropriate. Do not limit them to a brief token response.
                - Ordinary preferences, small decisions, and everyday movement may carry the
                  scene forward without asking the user for permission at every step.
                - Preserve the user's stated actions, words, intentions, and characterization.
                  Leave high-stakes commitments, major relationship changes, dangerous acts,
                  intimacy, and irreversible outcomes open unless the user has directed them.
                - A sleeping or solitary player need not speak. Describe a lived scene rather
                  than pulling absent NPCs back in just to create a conversation.
                """;
        return """

                Fiction-writing and pacing:
                - Write a developed scene, not a terse status report or a paraphrase of the input.
                  Show purposeful action, concrete sensory detail, pauses, tone, and character
                  responses that connect one beat to the next and reveal personality or subtext.
                - For a substantial scene or automatic continuation, usually develop several
                  narrative paragraphs interleaved with dialogue; scale length to the scene and
                  the user's requested style. A short question or meta command may stay concise.
                - Let the next beat grow from the situation. Do not repeatedly end with generic
                  prompts such as "What will you do now?" or "Are you ready to continue?".
                - Avoid a mechanical roll call in which every NPC gets one line. Use only those
                  present and relevant; departing characters stay absent under the cast rules.
                - Concrete detail must make physical sense: five minutes is not five ticks of
                  a clock's second hand. Do not add unsupported smells, gestures, or repeated
                  appearance descriptions as filler.
                - Preserve authoritative scene time and state while writing expressive prose.
                  Richer description by itself does not imply extra elapsed time.
                - Keep neutral OOC answers and structured-state extraction tasks concise and
                  follow their requested format; the fiction-writing guidance applies to fiction.
                """ + participation;
    }
}
