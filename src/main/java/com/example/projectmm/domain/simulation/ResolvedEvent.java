package com.example.projectmm.domain.simulation;

import com.example.projectmm.domain.memory.MemoryAccessChange;
import com.example.projectmm.domain.relationship.RelationshipDelta;

import java.time.LocalDateTime;

public record ResolvedEvent(
        LocalDateTime occurredAt,
        String eventType,
        String summary,
        int importance,
        int narrativeAnchor,
        StateDelta stateDelta,
        Long relationshipTargetCharacterId,
        RelationshipDelta relationshipDelta,
        EventSource source,
        MemoryAccessChange memoryAccessChange
) {
    public ResolvedEvent(
            LocalDateTime occurredAt,
            String eventType,
            String summary,
            int importance,
            StateDelta stateDelta,
            Long relationshipTargetCharacterId,
            RelationshipDelta relationshipDelta,
            EventSource source
    ) {
        this(
                occurredAt,
                eventType,
                summary,
                importance,
                0,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                source,
                null
        );
    }

    public ResolvedEvent(
            LocalDateTime occurredAt,
            String eventType,
            String summary,
            int importance,
            StateDelta stateDelta,
            Long relationshipTargetCharacterId,
            RelationshipDelta relationshipDelta,
            EventSource source,
            MemoryAccessChange memoryAccessChange
    ) {
        this(
                occurredAt,
                eventType,
                summary,
                importance,
                0,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                source,
                memoryAccessChange
        );
    }
}