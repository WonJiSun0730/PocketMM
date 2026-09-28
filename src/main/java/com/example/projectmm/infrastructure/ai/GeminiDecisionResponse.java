package com.example.projectmm.infrastructure.ai;

import tools.jackson.databind.JsonNode;

public record GeminiDecisionResponse(
        String eventType,
        String summary,
        int importance,
        Integer narrativeAnchor,
        int energyChange,
        int moodChange,
        int memoryAbilityChange,
        Long relationshipTargetCharacterId,
        int affinityChange,
        int trustChange,
        int tensionChange,
        JsonNode memoryAccessChange
) {

    public int resolvedNarrativeAnchor() {
        return narrativeAnchor == null
                ? 0
                : narrativeAnchor;
    }
}