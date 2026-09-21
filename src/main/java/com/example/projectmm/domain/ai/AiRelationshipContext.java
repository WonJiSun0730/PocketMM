package com.example.projectmm.domain.ai;

public record AiRelationshipContext(
        Long characterId,
        String characterName,
        int affinity,
        int trust,
        int tension
) {
}