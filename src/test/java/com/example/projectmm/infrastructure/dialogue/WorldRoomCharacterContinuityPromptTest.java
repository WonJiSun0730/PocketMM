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
                prompt.indexOf("Scene-state authority rules:"));
        String marbasProfile = profiles.substring(profiles.indexOf("ID 10"), profiles.indexOf("ID 20"));
        String studentProfile = profiles.substring(profiles.indexOf("ID 20"));
        assertThat(marbasProfile).doesNotContain("presence:").contains("rawDescription: Speak British English with Korean translation.");
        assertThat(studentProfile).contains("Korean by default").doesNotContain("British English");
        assertThat(prompt).contains("ID 10 | Marbas | presence: DEPARTED", "ID 20 | 수험생 A | presence: ACTIVE");
        assertThat(prompt).contains("Later departures and the latest user direction take precedence.",
                "Do not invent a school visit", "모든 등장인물이 나올 것");
        assertThat(prompt).contains("Player action elaboration is enabled", "Never invent new player dialogue",
                "not a terse status report", "Preserve authoritative scene time and state");
        assertThat(prompt).contains("does not prove that an event never happened",
                "Do not pretend to forget facts visible in that context",
                "may acknowledge it, ask useful questions, and act on it",
                "Preserve the source and uncertainty of a report",
                "never gives characters access to private thoughts");
        assertThat(prompt).doesNotContain("Do not narrate the player's meaningful choices, emotions");
        var strict = new WorldRoomDialogueContext(context.roomId(), context.roomTitle(), context.visibility(),
                context.simulatedAt(), context.world(), context.playerProfile(), context.introTitle(),
                context.introContent(), context.recentMessages(), context.roomCast(), context.activeCast(),
                context.sessionSystems(), context.playerState(), context.recalledMemories(), "", "", true);
        var moved = new WorldRoomDialogueContext(context.roomId(), context.roomTitle(), context.visibility(),
                context.simulatedAt().plusHours(1), context.world(), context.playerProfile(), context.introTitle(),
                context.introContent(), context.recentMessages(), context.roomCast(), context.activeCast(),
                context.sessionSystems(), context.playerState(), context.recalledMemories(), "", "", false);
        var gateway = new GeminiDialogueGateway("test", "test", new ObjectMapper());
        var movedPrompt = gateway.buildWorldRoomSystemPrompt(moved);
        // Simulation time changes must not invalidate rules, world or character prefix.
        assertThat(movedPrompt.substring(0, movedPrompt.indexOf("Current simulation time:")))
                .isEqualTo(prompt.substring(0, prompt.indexOf("Current simulation time:")));
        String quote="은색 열쇠에 제비 문양이 있었다.";
        var withMemory = new WorldRoomDialogueContext(context.roomId(), context.roomTitle(), context.visibility(),
                context.simulatedAt(), context.world(), context.playerProfile(), context.introTitle(),
                context.introContent(), context.recentMessages(), context.roomCast(), context.activeCast(),
                context.sessionSystems(), context.playerState(), List.of(new WorldRoomDialogueContext.CharacterMemories(
                        20L,"수험생 A",List.of(new WorldRoomDialogueContext.RecalledMemory(
                        1L,"사용자가 말한 내용: " + quote,quote,7,70,context.simulatedAt())))), "", "", false);
        String memoryPrompt=gateway.buildWorldRoomSystemPrompt(withMemory);
        assertThat(memoryPrompt.split(java.util.regex.Pattern.quote(quote),-1)).hasSize(2);
        String strictPrompt = new GeminiDialogueGateway("test", "test", new ObjectMapper())
                .buildWorldRoomSystemPrompt(strict);
        assertThat(strictPrompt).contains("Player authorship is reserved to the user:",
                "Never supply new player dialogue").doesNotContain("Player action elaboration is enabled");
    }
}
