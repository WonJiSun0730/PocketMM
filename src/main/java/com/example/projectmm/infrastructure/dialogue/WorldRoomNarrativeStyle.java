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
                Player action elaboration is enabled; player authorship stays with the user:
                - Expand the concrete execution of an action the user has already established.
                  Describe its materials, tools, physical steps, sensory detail, and direct
                  consequences without merely repeating the user's sentence.
                - Fill routine procedural details within that action; do not ask the user to
                  specify every ingredient, utensil, hand movement, or other harmless detail.
                  Match the stated stage: preparing to bake does not authorize finishing the
                  baking, delivering the cookies, or choosing their recipient.
                - Never invent new player dialogue, intent, opinion, knowledge, emotion,
                  preference, agreement, refusal, promise, or independent next objective.
                  A profile establishes characterization, not permission to perform choices.
                - Judge a gesture by its meaning, not its length: a nod that accepts a proposal,
                  a smile that confirms affection, or a one-line answer can usurp authorship.
                - Example: if the user says Millie prepared cookie cutters and got ready to bake,
                  describe cutter shapes, laying out ingredients, or mixing butter and sugar
                  as steps of that preparation. Do not make her scold another character about
                  baking, volunteer help, express an unprovided opinion, or start a new exchange.
                - A knock authorizes describing the knock and NPC response, not entering,
                  handing over documents, answering a briefing, and accepting a tour in sequence.
                - Preserve dialogue and intentions already supplied by the user; do not add
                  witty rejoinders or establish that the player has read or understood a briefing.
                  World facts and the player's knowledge of those facts are separate.
                - An explicit current user instruction may delegate a specific player response
                  or task. Honor only that scope; do not treat automatic continuation, a character
                  profile, or prior AI-authored player behavior as new authorization.
                - Advance NPCs and the environment naturally. When the next development needs
                  a fresh player answer or decision, leave that opening for the user; never
                  simulate repeated NPC-question/player-answer loops in one response.
                - Do not freeze the scene or demand detailed narration to avoid overreach.
                  Keep prose rich within the established action and NPC/world developments.
                  A sleeping or solitary player need not speak or acquire an invented companion.
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
                  A natural NPC question or unresolved moment may end the beat without supplying
                  the player's answer; developed prose does not require completing an exchange.
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
