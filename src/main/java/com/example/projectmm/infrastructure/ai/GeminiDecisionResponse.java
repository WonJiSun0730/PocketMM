package com.example.projectmm.infrastructure.ai;

import tools.jackson.databind.JsonNode;

import java.util.List;

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
        JsonNode memoryAccessChange,
        List<GeminiMemoryDetailResponse> memoryDetails,
        GeminiRelationshipFactChangeResponse relationshipFactChange
) {

    public GeminiDecisionResponse(
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
            JsonNode memoryAccessChange,
            List<GeminiMemoryDetailResponse> memoryDetails
    ) {
        this(
                eventType,
                summary,
                importance,
                narrativeAnchor,
                energyChange,
                moodChange,
                memoryAbilityChange,
                relationshipTargetCharacterId,
                affinityChange,
                trustChange,
                tensionChange,
                memoryAccessChange,
                memoryDetails,
                null
        );
    }

    public int resolvedNarrativeAnchor() {
        return narrativeAnchor == null
                ? 0
                : narrativeAnchor;
    }
}