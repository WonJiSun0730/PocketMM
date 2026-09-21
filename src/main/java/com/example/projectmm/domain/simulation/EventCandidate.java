package com.example.projectmm.domain.simulation;

import java.time.LocalDateTime;

public record EventCandidate(
        LocalDateTime occurredAt,
        String eventType,
        String summary,
        int importance,
        boolean requiresAi,
        StateDelta stateDelta
) {
}