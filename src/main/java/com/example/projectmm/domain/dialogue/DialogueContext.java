package com.example.projectmm.domain.dialogue;

import com.example.projectmm.application.policy.ContentMode;

import java.time.LocalDateTime;
import java.util.List;

public record DialogueContext(
        Long characterId,
        String characterName,
        DialogueCharacterProfile characterProfile,
        int energy,
        int mood,
        LocalDateTime simulatedAt,
        ContentMode contentMode,
        List<DialogueMessageContext> recentMessages,
        List<DialogueMemoryContext> memories,
        List<DialogueRelationshipContext> relationships,
        DialoguePlayerProfile playerProfile,
        int playerEnergy,
        int playerMood,
        List<DialogueCustomState> customStates,
        List<DialogueRelationshipFactContext> relationshipFacts
) {

    public DialogueContext(
            Long characterId,
            String characterName,
            DialogueCharacterProfile characterProfile,
            int energy,
            int mood,
            LocalDateTime simulatedAt,
            ContentMode contentMode,
            List<DialogueMessageContext> recentMessages,
            List<DialogueMemoryContext> memories,
            List<DialogueRelationshipContext> relationships,
            DialoguePlayerProfile playerProfile,
            int playerEnergy,
            int playerMood,
            List<DialogueCustomState> customStates
    ) {
        this(
                characterId,
                characterName,
                characterProfile,
                energy,
                mood,
                simulatedAt,
                contentMode,
                recentMessages,
                memories,
                relationships,
                playerProfile,
                playerEnergy,
                playerMood,
                customStates,
                List.of()
        );
    }
}