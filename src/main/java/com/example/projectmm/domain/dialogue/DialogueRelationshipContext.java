package com.example.projectmm.domain.dialogue;

public record DialogueRelationshipContext(
        Long characterId,
        String characterName,
        int affinity,
        int trust,
        int tension
) {
}