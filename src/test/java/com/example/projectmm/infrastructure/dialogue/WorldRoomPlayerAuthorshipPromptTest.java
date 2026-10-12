package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.WorldRoomContinuationPrompt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldRoomPlayerAuthorshipPromptTest {
    @Test
    void allowsProceduralDetailWithoutInventingPlayerIntentOrBlockingTheScene() {
        String prompt = WorldRoomNarrativeStyle.instruction(false);
        assertThat(prompt).contains("Fill routine procedural details within that action",
                "cutter shapes", "mixing butter and sugar", "Do not make her scold",
                "preparing to bake does not authorize finishing",
                "Never invent new player dialogue, intent, opinion, knowledge, emotion",
                "a nod that accepts a proposal", "World facts and the player's knowledge",
                "Do not freeze the scene or demand detailed narration",
                "not permission to perform choices", "prior AI-authored player behavior",
                "not a terse status report");
        assertThat(prompt).doesNotContain("their natural dialogue", "everyday movement may carry",
                "Do not limit them to a brief token response");
    }

    @Test
    void continuationDoesNotDelegateANewPlayerAnswerAndStrictModeStaysStrict() {
        assertThat(WorldRoomContinuationPrompt.build(false)).contains(
                "Elaborate only the concrete steps", "within their stated stage and scope",
                "Automatic continuation is not permission", "Never invent new player dialogue",
                "Even a nod or a short answer", "Stop before a fresh player answer or decision",
                "do not freeze the scene", "previous AI-authored player behavior")
                .doesNotContain("natural spoken contributions", "everyday choices",
                        "thoughts and feelings consistent");
        assertThat(WorldRoomNarrativeStyle.instruction(true)).contains(
                "Never supply new player dialogue, actions, thoughts, feelings, decisions",
                "Faithfully acknowledge")
                .doesNotContain("Fill routine procedural details", "cutter shapes");
        assertThat(WorldRoomContinuationPrompt.build(true)).contains(
                "Do not invent any player dialogue, action, thought, feeling, reaction, decision, or movement")
                .doesNotContain("Fill routine procedural details");
    }
}
