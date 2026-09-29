package com.example.projectmm.domain.simulation;

import com.example.projectmm.application.memory.MemoryDetailCandidate;
import com.example.projectmm.domain.memory.MemoryAccessChange;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.relationship.RelationshipFactChange;

import java.time.LocalDateTime;
import java.util.List;

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
        MemoryAccessChange memoryAccessChange,
        List<MemoryDetailCandidate> memoryDetails,
        RelationshipFactChange relationshipFactChange
) {

    public ResolvedEvent {
        memoryDetails = memoryDetails == null
                ? List.of()
                : List.copyOf(memoryDetails);

        relationshipFactChange =
                relationshipFactChange == null
                        ? RelationshipFactChange.none()
                        : relationshipFactChange;
    }

    public ResolvedEvent(
            LocalDateTime occurredAt,
            String eventType,
            String summary,
            int importance,
            int narrativeAnchor,
            StateDelta stateDelta,
            Long relationshipTargetCharacterId,
            RelationshipDelta relationshipDelta,
            EventSource source,
            MemoryAccessChange memoryAccessChange,
            List<MemoryDetailCandidate> memoryDetails
    ) {
        this(
                occurredAt,
                eventType,
                summary,
                importance,
                narrativeAnchor,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                source,
                memoryAccessChange,
                memoryDetails,
                RelationshipFactChange.none()
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
                null,
                List.of()
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
                memoryAccessChange,
                List.of()
        );
    }

    public ResolvedEvent(
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
        this(
                occurredAt,
                eventType,
                summary,
                importance,
                narrativeAnchor,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                source,
                memoryAccessChange,
                List.of()
        );
    }
}