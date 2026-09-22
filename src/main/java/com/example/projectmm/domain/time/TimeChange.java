package com.example.projectmm.domain.time;

import java.time.LocalDateTime;

public record TimeChange(
        boolean changed,
        LocalDateTime previousTime,
        LocalDateTime newTime,
        String sourceExpression
) {

    public static TimeChange unchanged(
            LocalDateTime currentTime
    ) {
        return new TimeChange(
                false,
                currentTime,
                currentTime,
                null
        );
    }

    public static TimeChange changed(
            LocalDateTime previousTime,
            LocalDateTime newTime,
            String sourceExpression
    ) {
        return new TimeChange(
                true,
                previousTime,
                newTime,
                sourceExpression
        );
    }
}