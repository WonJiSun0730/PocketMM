package com.example.projectmm.domain.simulation;

import com.example.projectmm.domain.relationship.RelationshipDelta;

import java.time.LocalDateTime;

public record ResolvedEvent(
        LocalDateTime occurredAt,
        String eventType,
        String summary,
        int importance,
        StateDelta stateDelta,
        Long relationshipTargetCharacterId,
        RelationshipDelta relationshipDelta,
        EventSource source
) {
}