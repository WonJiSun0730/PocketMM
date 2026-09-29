package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import com.example.projectmm.domain.memory.MemoryTier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class MemoryTierPolicy {

    private static final long HOT_ACCESS_DAYS = 7;

    public MemoryTier determine(
            Memory memory,
            LocalDateTime currentTime
    ) {
        if (memory == null) {
            throw new IllegalArgumentException(
                    "memory는 null일 수 없습니다."
            );
        }

        if (currentTime == null) {
            throw new IllegalArgumentException(
                    "currentTime은 null일 수 없습니다."
            );
        }

        if (recentlyActive(memory, currentTime)) {
            return MemoryTier.HOT;
        }

        MemoryDetailLevel detailLevel =
                memory.getDetailLevel();

        if (detailLevel == null) {
            return MemoryTier.WARM;
        }

        return switch (detailLevel) {
            case DETAILED, SUMMARY ->
                    MemoryTier.WARM;

            case PATTERN, SCHEMA ->
                    MemoryTier.COLD;
        };
    }

    private boolean recentlyActive(
            Memory memory,
            LocalDateTime currentTime
    ) {
        LocalDateTime latestActivity =
                latest(
                        memory.getLastAccessedAt(),
                        memory.getLastReactivatedAt()
                );

        if (latestActivity == null
                || latestActivity.isAfter(currentTime)) {
            return false;
        }

        return Duration.between(
                latestActivity,
                currentTime
        ).toDays() <= HOT_ACCESS_DAYS;
    }

    private LocalDateTime latest(
            LocalDateTime first,
            LocalDateTime second
    ) {
        if (first == null) {
            return second;
        }

        if (second == null) {
            return first;
        }

        return first.isAfter(second)
                ? first
                : second;
    }
}