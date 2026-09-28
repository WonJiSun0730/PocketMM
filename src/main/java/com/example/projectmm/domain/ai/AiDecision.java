package com.example.projectmm.domain.ai;

import com.example.projectmm.domain.memory.MemoryAccessChange;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.StateDelta;

public record AiDecision(
        String eventType,
        String summary,
        int importance,
        int narrativeAnchor,
        StateDelta stateDelta,
        Long relationshipTargetCharacterId,
        RelationshipDelta relationshipDelta,
        MemoryAccessChange memoryAccessChange
) {

    public AiDecision(
            String eventType,
            String summary,
            int importance,
            StateDelta stateDelta,
            Long relationshipTargetCharacterId,
            RelationshipDelta relationshipDelta
    ) {
        this(
                eventType,
                summary,
                importance,
                0,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                null
        );
    }

    public AiDecision(
            String eventType,
            String summary,
            int importance,
            StateDelta stateDelta,
            Long relationshipTargetCharacterId,
            RelationshipDelta relationshipDelta,
            MemoryAccessChange memoryAccessChange
    ) {
        this(
                eventType,
                summary,
                importance,
                0,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                memoryAccessChange
        );
    }
}