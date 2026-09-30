package com.example.projectmm.domain.ai;

import com.example.projectmm.application.memory.MemoryDetailCandidate;
import com.example.projectmm.domain.memory.MemoryAccessChange;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.relationship.RelationshipFactChange;
import com.example.projectmm.domain.simulation.StateDelta;

import java.util.List;

public record AiDecision(
        String eventType,
        String summary,
        String outcome,
        int importance,
        int narrativeAnchor,
        StateDelta stateDelta,
        Long relationshipTargetCharacterId,
        RelationshipDelta relationshipDelta,
        MemoryAccessChange memoryAccessChange,
        List<MemoryDetailCandidate> memoryDetails,
        RelationshipFactChange relationshipFactChange
) {

    public AiDecision {
        outcome =
                outcome == null
                        ? ""
                        : outcome.strip();

        memoryDetails = memoryDetails == null
                ? List.of()
                : List.copyOf(memoryDetails);

        relationshipFactChange =
                relationshipFactChange == null
                        ? RelationshipFactChange.none()
                        : relationshipFactChange;
    }

    public AiDecision(
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
        this(
                eventType,
                summary,
                "",
                importance,
                narrativeAnchor,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                memoryAccessChange,
                memoryDetails,
                RelationshipFactChange.none()
        );
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
                "",
                importance,
                0,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                null,
                List.of(),
                RelationshipFactChange.none()
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
                "",
                importance,
                0,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                memoryAccessChange,
                List.of(),
                RelationshipFactChange.none()
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
                "",
                importance,
                narrativeAnchor,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                memoryAccessChange,
                List.of(),
                RelationshipFactChange.none()
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
            MemoryAccessChange memoryAccessChange,
            List<MemoryDetailCandidate> memoryDetails,
            RelationshipFactChange relationshipFactChange
    ) {
        this(
                eventType,
                summary,
                "",
                importance,
                narrativeAnchor,
                stateDelta,
                relationshipTargetCharacterId,
                relationshipDelta,
                memoryAccessChange,
                memoryDetails,
                relationshipFactChange
        );
    }
}