package com.example.projectmm.domain.ai;

import com.example.projectmm.domain.simulation.StateDelta;

public record AiDecision(
        String eventType,
        String summary,
        int importance,
        StateDelta stateDelta
) {
}