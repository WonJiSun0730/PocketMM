package com.example.projectmm.domain.ai;

public record AiRelationshipFactContext(
        Long targetCharacterId,
        String targetCharacterName,
        String relation,
        String value
) {
}