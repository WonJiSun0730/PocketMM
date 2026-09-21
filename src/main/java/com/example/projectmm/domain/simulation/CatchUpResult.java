package com.example.projectmm.domain.simulation;

import java.time.Duration;
import java.time.LocalDateTime;

public record CatchUpResult(
        LocalDateTime from,
        LocalDateTime to,
        Duration actualElapsed,
        Duration simulatedElapsed,
        boolean capped
) {
}