package com.example.projectmm.infrastructure.ai;

public record GeminiDecisionResponse(
        String eventType,
        String summary,
        int importance,
        int energyChange,
        int moodChange,
        Long relationshipTargetCharacterId,
        int affinityChange,
        int trustChange,
        int tensionChange
) {
}