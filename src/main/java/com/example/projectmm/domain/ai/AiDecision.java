package com.example.projectmm.domain.ai;

import com.example.projectmm.application.memory.MemoryDetailCandidate;
import com.example.projectmm.domain.memory.MemoryAccessChange;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.StateDelta;

import java.util.List;

public record AiDecision(
        String eventType,
        String summary,
        int importance,
        int narrativeAnchor,
        StateDelta stateDelta,
        Long relationshipTargetCharacterId,
        RelationshipDelta relationshipDelta,
        MemoryAccessChange memoryAccessChange,
        List<MemoryDetailCandidate> memoryDetails
) {

    public AiDecision {
        memoryDetails = memoryDetails == null
                ? List.of()
                : List.copyOf(memoryDetails);
    }

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
                null,
                List.of()
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
                memoryAccessChange,
                List.of()
        );
    }

    public AiDecision(
            String eventType,
            String summary,
            int importance,
            int narrativeAnchor,
            StateDelta stateDelta,
            Long relationshipTargetCharacterId,
            RelationshipDelta relationshipDelta,
            MemoryAccessChange memoryAccessChange
    ) {
        this(
                eventType,
                summary,
                importance,
                narrativeAnchor,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                memoryAccessChange,
                List.of()
        );
    }
}