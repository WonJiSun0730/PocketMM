package com.example.projectmm.domain.time;

public record AiTimeParseResult(
        boolean matched,
        TimeType type,
        Long amount,
        TimeUnit unit,
        DayPart dayPart,
        TimePrecision precision,
        Double confidence,
        String sourceExpression,
        DurationScale durationScale,
        java.time.LocalDateTime targetTime
) {

    public AiTimeParseResult(boolean matched, TimeType type, Long amount, TimeUnit unit, DayPart dayPart,
                             TimePrecision precision, Double confidence, String sourceExpression,
                             DurationScale durationScale) {
        this(matched, type, amount, unit, dayPart, precision, confidence, sourceExpression, durationScale, null);
    }

    public enum DurationScale {
        SHORT, MEDIUM, LONG, UNSPECIFIED
    }

    public enum TimePrecision {
        EXACT,
        APPROXIMATE,
        AMBIGUOUS
    }

    public enum TimeType {
        RELATIVE,
        NEXT_DAY,
        SAME_DAY_PART,
        SCENE_TIME,
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
