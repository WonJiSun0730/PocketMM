package com.example.projectmm.domain.dialogue;

public record WorldRoomCharacterContext(
        Long characterId,
        String characterName,
        DialogueCharacterProfile profile,
        int energy,
        int mood,
        boolean active
) {}
