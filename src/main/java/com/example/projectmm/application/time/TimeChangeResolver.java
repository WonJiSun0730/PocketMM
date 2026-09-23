package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult;
import com.example.projectmm.domain.time.TimeChange;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TimeChangeResolver {

    private static final double MIN_CONFIDENCE = 0.8;

    public TimeChange resolve(
            AiTimeParseResult result,
            LocalDateTime currentTime
    ) {
        if (result == null
                || !result.matched()
                || result.confidence() == null
                || result.confidence() < MIN_CONFIDENCE) {

            return TimeChange.unchanged(currentTime);
        }

        LocalDateTime newTime =
                switch (result.type()) {
                    case RELATIVE ->
                            resolveRelative(result, currentTime);

                    case NEXT_DAY ->
                            resolveNextDay(result, currentTime);

                    case SAME_DAY_PART ->
                            resolveSameDayPart(result, currentTime);

                    case UNKNOWN ->
                            currentTime;
                };

        if (newTime.equals(currentTime)) {
            return TimeChange.unchanged(currentTime);
        }

        return TimeChange.changed(
                currentTime,
                newTime,
                result.sourceExpression()
        );
    }

    private LocalDateTime resolveRelative(
            AiTimeParseResult result,
            LocalDateTime currentTime
    ) {
        if (result.amount() == null
                || result.amount() <= 0
                || result.unit() == null) {

            return currentTime;
        }

        return switch (result.unit()) {
            case MINUTE ->
                    currentTime.plusMinutes(result.amount());

            case HOUR ->
                    currentTime.plusHours(result.amount());

            case DAY ->
                    currentTime.plusDays(result.amount());
        };
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