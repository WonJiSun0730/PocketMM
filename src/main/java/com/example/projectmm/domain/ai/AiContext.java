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
        List<AiMemoryContext>memories,
        List<AiRelationshipContext> relationships
) {
}