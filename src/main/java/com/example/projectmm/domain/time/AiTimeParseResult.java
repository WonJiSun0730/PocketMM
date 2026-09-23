package com.example.projectmm.domain.time;

public record AiTimeParseResult(
        boolean matched,
        TimeType type,
        Long amount,
        TimeUnit unit,
        DayPart dayPart,
        Double confidence,
        String sourceExpression
) {

    public enum TimeType {
        RELATIVE,
        NEXT_DAY,
        SAME_DAY_PART,
        UNKNOWN
    }

    public enum TimeUnit {
        MINUTE,
        HOUR,
        DAY
    }

    public enum DayPart {
        MORNING,
        NOON,
        AFTERNOON,
        EVENING,
        NIGHT
    }
}