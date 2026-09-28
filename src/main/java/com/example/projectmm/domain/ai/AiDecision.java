package com.example.projectmm.domain.ai;

import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.StateDelta;
import com.example.projectmm.domain.memory.MemoryAccessChange;

public record AiDecision(
        String eventType,
        String summary,
        int importance,
        StateDelta stateDelta,
        Long relationshipTargetCharacterId,
        RelationshipDelta relationshipDelta,
        MemoryAccessChange memoryAccessChange
) {
    public AiDecision(String eventType, String summary, int importance, StateDelta stateDelta,
                      Long relationshipTargetCharacterId, RelationshipDelta relationshipDelta) {
        this(eventType, summary, importance, stateDelta, relationshipTargetCharacterId, relationshipDelta, null);
    }
}
