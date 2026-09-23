package com.example.projectmm.domain.time;

import java.time.LocalDateTime;
import java.time.Duration;

public record TimeChange(
        boolean changed,
        LocalDateTime previousTime,
        LocalDateTime newTime,
        String sourceExpression,
        Long expectedElapsedMinutes
) {

    public static TimeChange unchanged(
            LocalDateTime currentTime
    ) {
        return unchanged(currentTime, null, null);
    }

    public static TimeChange unchanged(
            LocalDateTime currentTime,
            String sourceExpression,
            Long expectedElapsedMinutes
    ) {
        return new TimeChange(
                false,
                currentTime,
                currentTime,
                sourceExpression,
                expectedElapsedMinutes
        );
    }

    public static TimeChange changed(
            LocalDateTime previousTime,
            LocalDateTime newTime,
            String sourceExpression
    ) {
        return changed(previousTime, newTime, sourceExpression,
                Duration.between(previousTime, newTime).toMinutes());
    }

    public static TimeChange changed(
            LocalDateTime previousTime,
            LocalDateTime newTime,
            String sourceExpression,
            Long expectedElapsedMinutes
    ) {
        return new TimeChange(
                true,
                previousTime,
                newTime,
                sourceExpression,
                expectedElapsedMinutes
        );
    }

    public long appliedElapsedMinutes() {
        return Duration.between(previousTime, newTime).toMinutes();
    }
}
