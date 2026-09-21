package com.example.projectmm.domain.simulation;

import java.time.LocalDateTime;

public record ResolvedEvent(
        LocalDateTime occurredAt,
        String eventType,
        String summary,
        int importance,
        StateDelta stateDelta,
        EventSource source
) {
}