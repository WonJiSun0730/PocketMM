package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult;
import com.example.projectmm.domain.time.TimeChange;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;

@Component
public class TimeChangeResolver {

    private static final double MIN_CONFIDENCE = 0.8;

    public TimeChange resolve(
            AiTimeParseResult result,
            LocalDateTime currentTime
    ) {
        if (result == null
                || !result.matched()) {

            return TimeChange.unchanged(currentTime);
        }

        Long expectedMinutes = expectedElapsedMinutes(result, currentTime);
        if (result.confidence() == null || !Double.isFinite(result.confidence())
                || result.confidence() < MIN_CONFIDENCE) {
            return TimeChange.unchanged(currentTime, result.sourceExpression(), expectedMinutes);
        }

        LocalDateTime newTime =
                switch (result.type()) {
                    case RELATIVE ->
                            resolveRelative(result, currentTime);

                    case NEXT_DAY ->
                            resolveNextDay(result, currentTime);

                    case SAME_DAY_PART ->
                            resolveSameDayPart(result, currentTime);

                    case SCENE_TIME -> resolveSceneTime(result, currentTime);

                    case UNKNOWN ->
                            currentTime;
                };

        if (newTime.equals(currentTime)) {
            return TimeChange.unchanged(currentTime, result.sourceExpression(), expectedMinutes);
        }

        return TimeChange.changed(
                currentTime,
                newTime,
                result.sourceExpression(),
                expectedMinutes
        );
    }

    // Baseline before approximate variation or application guards.
    private Long expectedElapsedMinutes(AiTimeParseResult result, LocalDateTime currentTime) {
        if (result.type() == AiTimeParseResult.TimeType.RELATIVE
                && result.precision() == AiTimeParseResult.TimePrecision.AMBIGUOUS) {
            return null;
        }
        return switch (result.type()) {
            case RELATIVE -> relativeMinutes(result);
            case NEXT_DAY -> Duration.between(currentTime, resolveNextDay(result, currentTime)).toMinutes();
            case SAME_DAY_PART -> result.dayPart() == null ? null
                    : Duration.between(currentTime, applyDayPart(currentTime, result.dayPart())).toMinutes();
            case SCENE_TIME -> result.targetTime() == null ? null
                    : Duration.between(currentTime, resolveSceneTime(result, currentTime)).toMinutes();
            case UNKNOWN -> null;
        };
    }

    private Long relativeMinutes(AiTimeParseResult result) {
        if (result.amount() == null || result.amount() <= 0 || result.unit() == null) {
            return null;
        }
        return switch (result.unit()) {
            case MINUTE -> result.amount();
            case HOUR -> result.amount() * 60;
            case DAY -> result.amount() * 24 * 60;
        };
    }

    private LocalDateTime resolveSceneTime(AiTimeParseResult result, LocalDateTime currentTime) {
        var target = result.targetTime();
        if (target == null || result.precision() == AiTimeParseResult.TimePrecision.AMBIGUOUS
                || result.sourceExpression() == null || result.sourceExpression().isBlank()
                || target.isBefore(currentTime.minusDays(36500))
                || target.isAfter(currentTime.plusDays(36500))) return currentTime;
        // Compound events are already resolved into their final clock. Never vary it again.
        return target.withSecond(0).withNano(0);
    }

    private LocalDateTime resolveRelative(
            AiTimeParseResult result,
            LocalDateTime currentTime
    ) {
        if (result.precision() == AiTimeParseResult.TimePrecision.AMBIGUOUS) {
            return currentTime.plusMinutes(resolveAmbiguousMinutes(result, currentTime));
        }

        Long baseMinutes = relativeMinutes(result);
        if (baseMinutes == null) {

            return currentTime;
        }

        if (result.precision()
                != AiTimeParseResult.TimePrecision.APPROXIMATE) {

            return currentTime.plusMinutes(baseMinutes);
        }

        long adjustedMinutes =
                applyApproximateVariation(
                        baseMinutes,
                        result.sourceExpression(),
                        currentTime
                );

        return currentTime.plusMinutes(adjustedMinutes);
    }

    // Initial server policy for unquantified elapsed time. The parser chooses
    // a semantic scale only; even a supplied AI amount cannot override these bounds.
    private long resolveAmbiguousMinutes(AiTimeParseResult result, LocalDateTime currentTime) {
        if (result.durationScale() == null) {
            return 0;
        }
        int minimum;
        int maximum;
        switch (result.durationScale()) {
            case SHORT -> { minimum = 1; maximum = 5; }
            case MEDIUM -> { minimum = 10; maximum = 30; }
            case LONG -> { minimum = 30; maximum = 90; }
            default -> { return 0; }
        }
        String seed = currentTime + "|" + result.durationScale() + "|"
                + (result.sourceExpression() == null ? "" : result.sourceExpression());
        return minimum + Math.floorMod(seed.hashCode(), maximum - minimum + 1);
    }

    private long applyApproximateVariation(
            long baseMinutes,
            String sourceExpression,
            LocalDateTime currentTime
    ) {
        if (sourceExpression != null && sourceExpression.matches("(?s).*(?:잤|잔|자고|수면|slept).*")) {
            int margin = (int) Math.min(10, Math.max(1, baseMinutes / 20));
            int adjustment = Math.floorMod((currentTime + "|" + sourceExpression).hashCode(), margin * 2 + 1) - margin;
            return Math.max(1, baseMinutes + adjustment);
        }
        String seed =
                currentTime
                        + "|"
                        + (sourceExpression == null
                        ? ""
                        : sourceExpression);

        int bucket =
                Math.floorMod(
                        seed.hashCode(),
                        101
                );

        // 0.75 ~ 1.50 배
        double factor =
                0.75
                        + (bucket / 100.0) * 0.75;

        long adjustedMinutes =
                Math.round(baseMinutes * factor);

        return Math.max(1, adjustedMinutes);
    }

    private LocalDateTime resolveNextDay(
            AiTimeParseResult result,
            LocalDateTime currentTime
    ) {
        LocalDateTime newTime =
                currentTime.plusDays(1);

        if (result.dayPart() == null) {
            return newTime;
        }

        return applyDayPart(
                newTime,
                result.dayPart()
        );
    }

    private LocalDateTime resolveSameDayPart(
            AiTimeParseResult result,
            LocalDateTime currentTime
    ) {
        if (result.dayPart() == null) {
            return currentTime;
        }

        LocalDateTime newTime =
                applyDayPart(
                        currentTime,
                        result.dayPart()
                );

        // 같은 날의 과거 시각으로 되감기는 것은
        // 현재 단계에서는 자동 적용하지 않는다.
        if (newTime.isBefore(currentTime)) {
            return currentTime;
        }

        return newTime;
    }

    private LocalDateTime applyDayPart(
            LocalDateTime time,
            AiTimeParseResult.DayPart dayPart
    ) {
        int hour =
                switch (dayPart) {
                    case MORNING -> 8;
                    case NOON -> 12;
                    case AFTERNOON -> 15;
                    case EVENING -> 19;
                    case NIGHT -> 22;
                };

        return time
                .withHour(hour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }
}
