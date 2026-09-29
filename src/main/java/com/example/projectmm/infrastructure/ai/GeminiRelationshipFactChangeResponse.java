package com.example.projectmm.infrastructure.ai;

public record GeminiRelationshipFactChangeResponse(
        Long targetCharacterId,
        String relation,
        String value
) {
}