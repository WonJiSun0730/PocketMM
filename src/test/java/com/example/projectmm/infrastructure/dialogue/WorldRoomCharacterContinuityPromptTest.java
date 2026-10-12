package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.domain.chat.WorldConversation;
import com.example.projectmm.domain.dialogue.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorldRoomCharacterContinuityPromptTest {
    @Test
    void distinguishesDepartedCharacterAndKeepsAuthoredSpeechStyleInItsOwnProfile() {
        var marbas = new WorldRoomCharacterContext(10L, "Marbas",
                new DialogueCharacterProfile("Counsellor", "Speak British English with Korean translation.",
                        List.of(), null, "British English", null, null), 70, 70, false, true);
        var student = new WorldRoomCharacterContext(20L, "수험생 A", null, 70, 70, true);
        var context = new WorldRoomDialogueContext(1L, "Room", WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 19, 12, 0),
                new DialogueWorldContext(1L, "World", "", List.of(), List.of()), null,
                "자유시작", "자유로이 전개하시오.", List.of(), List.of(marbas, student),
                List.of(student), List.of());
        String prompt = new GeminiDialogueGateway("test", "test", new ObjectMapper())
                .buildWorldRoomSystemPrompt(context);
        String profiles = prompt.substring(prompt.indexOf("Characters allowed to participate in this room:"),
                prompt.indexOf("Characters currently present in the scene:"));
        String marbasProfile = profiles.substring(profiles.indexOf("ID 10"), profiles.indexOf("ID 20"));
        String studentProfile = profiles.substring(profiles.indexOf("ID 20"));
        assertThat(marbasProfile).contains("presence: DEPARTED", "rawDescription: Speak British English with Korean translation.");
        assertThat(studentProfile).contains("presence: ACTIVE", "Korean by default").doesNotContain("British English");
        assertThat(prompt).contains("Later departures and the latest user direction take precedence.",
                "Do not invent a school visit", "모든 등장인물이 나올 것");
        assertThat(prompt).contains("Player action elaboration is enabled", "Never invent new player dialogue",
                "not a terse status report", "Preserve authoritative scene time and state");
        assertThat(prompt).doesNotContain("Do not narrate the player's meaningful choices, emotions");
        var strict = new WorldRoomDialogueContext(context.roomId(), context.roomTitle(), context.visibility(),
                context.simulatedAt(), context.world(), context.playerProfile(), context.introTitle(),
                context.introContent(), context.recentMessages(), context.roomCast(), context.activeCast(),
                context.sessionSystems(), context.playerState(), context.recalledMemories(), "", "", true);
        String strictPrompt = new GeminiDialogueGateway("test", "test", new ObjectMapper())
                .buildWorldRoomSystemPrompt(strict);
        assertThat(strictPrompt).contains("Player authorship is reserved to the user:",
                "Never supply new player dialogue").doesNotContain("Player action elaboration is enabled");
    }
}
