package com.example.projectmm.domain.dialogue;

public record DialogueRelationshipFactContext(
        Long targetCharacterId,
        String targetCharacterName,
        String relation,
        String value
) {
}