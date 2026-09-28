package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class MemoryDecayPolicy {

    private static final int COMPRESSION_THRESHOLD = 30;

    public MemoryDecayResult calculate(
            Memory memory,
            LocalDateTime currentTime
    ) {
        long elapsedDays =
                Math.max(
                        0,
                        Duration.between(
                                memory.getRememberedAt(),
                                currentTime
                        ).toDays()
                );

        int decayPerDay =
                calculateDecayPerDay(
                        memory.getImportance()
                );

        long totalDecay =
                elapsedDays * decayPerDay;

        int decayedStrength =
                (int) Math.max(
                        0,
                        memory.getStrength() - totalDecay
                );

        int anchorFloor =
                Math.min(
                        memory.getStrength(),
                        memory.getNarrativeAnchor() * 10
                );

        int effectiveStrength =
                Math.max(
                        decayedStrength,
                        anchorFloor
                );

        boolean compressionRequired =
                decayedStrength <= COMPRESSION_THRESHOLD;

        return new MemoryDecayResult(
                memory.getStrength(),
                decayedStrength,
                effectiveStrength,
                compressionRequired
        );
    }

    private int calculateDecayPerDay(
            int importance
    ) {
        if (importance >= 8) {
            return 1;
        }

        if (importance >= 5) {
            return 2;
        }

        return 3;
    }
}