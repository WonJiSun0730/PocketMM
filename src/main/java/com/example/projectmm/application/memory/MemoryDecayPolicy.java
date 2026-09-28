package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class MemoryDecayPolicy {

    private static final int COMPRESSION_THRESHOLD = 70;

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

        LocalDateTime lastAccessedAt =
                memory.getLastAccessedAt() != null
                        ? memory.getLastAccessedAt()
                        : memory.getRememberedAt();

        long unaccessedDays =
                Math.max(
                        0,
                        Duration.between(
                                lastAccessedAt,
                                currentTime
                        ).toDays()
                );

        Long daysSinceReactivation =
                memory.getLastReactivatedAt() == null
                        ? null
                        : Math.max(
                        0,
                        Duration.between(
                                memory.getLastReactivatedAt(),
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

        int compressionPressure =
                calculateCompressionPressure(
                        memory,
                        decayedStrength,
                        elapsedDays,
                        unaccessedDays,
                        daysSinceReactivation
                );

        boolean compressionRequired =
                compressionPressure >= COMPRESSION_THRESHOLD;

        return new MemoryDecayResult(
                memory.getStrength(),
                decayedStrength,
                effectiveStrength,
                compressionRequired
        );
    }

    private int calculateCompressionPressure(
            Memory memory,
            int decayedStrength,
            long elapsedDays,
            long unaccessedDays,
            Long daysSinceReactivation
    ) {
        int pressure =
                Math.max(
                        0,
                        100 - decayedStrength
                );

        pressure += (int) Math.min(
                20,
                unaccessedDays / 7
        );

        pressure += (int) Math.min(
                10,
                elapsedDays / 30
        );

        pressure -= memory.getNarrativeAnchor() * 2;

        if (daysSinceReactivation != null) {
            if (daysSinceReactivation <= 7) {
                pressure -= 15;
            } else if (daysSinceReactivation <= 30) {
                pressure -= 5;
            }
        }

        return Math.max(0, pressure);
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