package com.example.projectmm.domain.ai;

import java.time.LocalDateTime;
import java.util.List;

public record AiContext(
        Long characterId,
        String characterName,
        int energy,
        int mood,
        LocalDateTime simulatedAt,
        String eventType,
        String eventSummary,
        List<AiMemoryContext> memories,
        List<AiRelationshipContext> relationships,
        List<AiRelationshipFactContext> relationshipFacts
) {

    public AiContext(
            Long characterId,
            String characterName,
            int energy,
            int mood,
            LocalDateTime simulatedAt,
            String eventType,
            String eventSummary,
            List<AiMemoryContext> memories,
            List<AiRelationshipContext> relationships
    ) {
        this(
                characterId,
                characterName,
                energy,
                mood,
                simulatedAt,
                eventType,
                eventSummary,
                memories,
                relationships,
                List.of()
        );
    }
}