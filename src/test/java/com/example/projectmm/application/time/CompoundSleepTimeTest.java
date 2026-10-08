package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult.TimePrecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CompoundSleepTimeTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 8, 12, 0);
    private final AiTimeParser parser = mock(AiTimeParser.class);
    private final HybridTimeInterpreter interpreter = new HybridTimeInterpreter(
            new RuleBasedTimeInterpreter(), new AiTimeInterpreter(parser, new TimeChangeResolver()));

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "3일 후, 오후 3시에 잠들어 6시간 잤다|2026-01-11T21:00",
            "3일 후, 오후 3시에 잠들었다가 6시간 자고 깼다.|2026-01-11T21:00",
            "3일 후의 오후 11시 30분에 잠들어 6시간 잤다|2026-01-12T05:30",
            "한 일 뒤, 오전 12시에 잠들어 두 시간 잤다|2026-01-09T02:00",
            "3일 후, 오후 12시에 잠들어 30분 잤다|2026-01-11T12:30"
    })
    void calculatesEveryEventInNarrativeOrder(String input, LocalDateTime target) {
        var result = interpreter.interpret(input, now);
        assertThat(result.precision()).isEqualTo(TimePrecision.EXACT);
        assertThat(result.timeChange().newTime()).isEqualTo(target);
        assertThat(result.timeChange().sourceExpression()).isEqualTo(input);
        verifyNoInteractions(parser);
    }

    @Test
    void approximationOnlyVariesSleepAndReproducesSameState() {
        String input = "3일 후, 오후 3시에 잠들어 6시간 정도 잤다";
        var result = interpreter.interpret(input, now);
        assertThat(result.precision()).isEqualTo(TimePrecision.APPROXIMATE);
        assertThat(result.timeChange().newTime()).isBetween(
                LocalDateTime.of(2026, 1, 11, 20, 50), LocalDateTime.of(2026, 1, 11, 21, 10));
        assertThat(result.timeChange().expectedElapsedMinutes()).isEqualTo(4860L);
        assertThat(interpreter.interpret(input, now)).isEqualTo(result);
        verifyNoInteractions(parser);
    }

    @Test
    void subjectAndReportedSleepDoNotLoseTheThreeDayJump() {
        String input = "3일 후, 수험생 B는 오후 3시에 잠들어 6시간정도 잔 것 같다.";
        var result = interpreter.interpret(input, now);
        assertThat(result.precision()).isEqualTo(TimePrecision.APPROXIMATE);
        assertThat(result.timeChange().newTime()).isBetween(
                LocalDateTime.of(2026, 1, 11, 20, 50), LocalDateTime.of(2026, 1, 11, 21, 10));
        assertThat(interpreter.interpret(input, now)).isEqualTo(result);
        verifyNoInteractions(parser);
    }

    @Test
    void invalidClockIsNotAppliedAsPartialDateJump() {
        var result = interpreter.interpret("3일 후, 오후 13시에 잠들어 6시간 잤다", now);
        assertThat(result.matched()).isFalse();
    }
}
