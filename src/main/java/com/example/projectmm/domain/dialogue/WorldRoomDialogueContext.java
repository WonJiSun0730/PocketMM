package com.example.projectmm.domain.dialogue;

import com.example.projectmm.domain.chat.WorldConversation;

import java.time.LocalDateTime;
import java.util.List;

public record WorldRoomDialogueContext(
        Long roomId,
        String roomTitle,
        WorldConversation.Visibility visibility,
        LocalDateTime simulatedAt,
        DialogueWorldContext world,
        DialoguePlayerProfile playerProfile,
        String introTitle,
        String introContent,
        List<DialogueMessageContext> recentMessages,
        List<WorldRoomCharacterContext> roomCast,
        List<WorldRoomCharacterContext> activeCast,
        List<WorldRoomSystemContext> sessionSystems,
        List<CharacterMemories> recalledMemories
) {
    public record CharacterMemories(
            Long characterId,
            String characterName,
            List<RecalledMemory> memories
    ) {}

    public record RecalledMemory(
            Long memoryId,
            String summary,
            String evidenceQuote,
            int importance,
            int effectiveStrength,
            LocalDateTime rememberedAt
    ) {}
}
