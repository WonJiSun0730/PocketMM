package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.simulation.CatchUpResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class CatchUpSimulationService {

    private static final Duration PROGRESS_CAP = Duration.ofDays(3);

    public CatchUpResult calculate(
            LocalDateTime lastSimulatedAt,
            LocalDateTime currentTime
    ) {
        Duration actualElapsed =
                Duration.between(lastSimulatedAt, currentTime);

        if (actualElapsed.isNegative() || actualElapsed.isZero()) {
            return new CatchUpResult(
                    lastSimulatedAt,
                    lastSimulatedAt,
                    Duration.ZERO,
                    Duration.ZERO,
                    false
            );
        }

        boolean capped =
                actualElapsed.compareTo(PROGRESS_CAP) > 0;

        Duration simulatedElapsed =
                capped ? PROGRESS_CAP : actualElapsed;

        LocalDateTime simulatedUntil =
                lastSimulatedAt.plus(simulatedElapsed);

        return new CatchUpResult(
                lastSimulatedAt,
                simulatedUntil,
                actualElapsed,
                simulatedElapsed,
                capped
        );
    }
}