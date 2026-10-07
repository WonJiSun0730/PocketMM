package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult;
import com.example.projectmm.domain.time.AiTimeParseResult.*;
import com.example.projectmm.domain.time.TimeInterpretation.Interpreter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TimeInspectorTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 23, 12, 0);
    private final AiTimeParser parser = mock(AiTimeParser.class);
    private final TimeChangeResolver resolver = new TimeChangeResolver();
    private final HybridTimeInterpreter interpreter = new HybridTimeInterpreter(
            new RuleBasedTimeInterpreter(), new AiTimeInterpreter(parser, resolver));

    @Test
    void exactRuleBypassesAiAndReportsSixtyMinutes() {
        var result = interpreter.interpret("정확히 한 시간이 지났다.", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.RULE);
        assertThat(result.precision()).isEqualTo(TimePrecision.EXACT);
        assertThat(result.timeChange().expectedElapsedMinutes()).isEqualTo(60L);
        assertThat(result.timeChange().appliedElapsedMinutes()).isEqualTo(60L);
        assertThat(result.timeChange().newTime()).isEqualTo(now.plusHours(1));
        verifyNoInteractions(parser);
    }

    @Test
    void approximateAiPreservesBaselineAndDeterministicVariation() {
        var parsed = relative(TimePrecision.APPROXIMATE, 0.95);
        when(parser.parse(any(), any())).thenReturn(parsed);
        var result = interpreter.interpret("한 시간 정도 지난 것 같아 시계를 봤다.", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.AI);
        assertThat(result.precision()).isEqualTo(TimePrecision.APPROXIMATE);
        assertThat(result.timeChange().expectedElapsedMinutes()).isEqualTo(60L);
        assertThat(result.timeChange().appliedElapsedMinutes()).isBetween(45L, 90L);
        assertThat(result.timeChange().sourceExpression()).isEqualTo("한 시간 정도");
        assertThat(resolver.resolve(parsed, now)).isEqualTo(result.timeChange());
        // Same conditions must retain the existing variation algorithm's result.
        int bucket = Math.floorMod((now + "|한 시간 정도").hashCode(), 101);
        long expectedApplied = Math.round(60 * (0.75 + bucket / 100.0 * 0.75));
        assertThat(result.timeChange().newTime()).isEqualTo(now.plusMinutes(expectedApplied));
    }

    @Test
    void exactAiDoesNotApplyVariation() {
        var change = resolver.resolve(relative(TimePrecision.EXACT, 0.95), now);
        assertThat(change.expectedElapsedMinutes()).isEqualTo(60L);
        assertThat(change.appliedElapsedMinutes()).isEqualTo(60L);
    }

    @Test
    void rejectedAiStillReportsInterpretationButAppliesZero() {
        when(parser.parse(any(), any())).thenReturn(relative(TimePrecision.APPROXIMATE, 0.5));
        var result = interpreter.interpret("한 시간 정도 지났다.", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.AI);
        assertThat(result.precision()).isEqualTo(TimePrecision.APPROXIMATE);
        assertThat(result.timeChange().changed()).isFalse();
        assertThat(result.timeChange().expectedElapsedMinutes()).isEqualTo(60L);
        assertThat(result.timeChange().appliedElapsedMinutes()).isZero();
        assertThat(result.timeChange().sourceExpression()).isEqualTo("한 시간 정도");
    }

    @Test
    void unquantifiedAmbiguousTimeHasNoInventedExpectedDuration() {
        when(parser.parse(any(), any())).thenReturn(new AiTimeParseResult(
                true, TimeType.RELATIVE, null, null, null,
                TimePrecision.AMBIGUOUS, 0.95, "한참 지났다", DurationScale.UNSPECIFIED));
        var result = interpreter.interpret("한참 지났다.", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.AI);
        assertThat(result.precision()).isEqualTo(TimePrecision.AMBIGUOUS);
        assertThat(result.timeChange().expectedElapsedMinutes()).isNull();
        assertThat(result.timeChange().appliedElapsedMinutes()).isZero();
        assertThat(result.timeChange().sourceExpression()).isEqualTo("한참 지났다");
    }

    @Test
    void completedThreeWeekDurationAdvancesAuthoritativeTime() {
        var result = interpreter.interpret(
                "그 뒤로 주기적으로 상담을 3주 정도 했습니다. 오늘은 상담이 없는 날이고, 저녁 시간이라 장을 보러 나왔어요.",
                now
        );

        assertThat(result.interpreter()).isEqualTo(Interpreter.RULE);
        assertThat(result.precision()).isEqualTo(TimePrecision.EXACT);
        assertThat(result.timeChange().newTime()).isEqualTo(now.plusWeeks(3));
        assertThat(result.timeChange().appliedElapsedMinutes()).isEqualTo(3L * 7 * 24 * 60);
        verifyNoInteractions(parser);
    }

    @Test
    void futureThreeWeekPlanDoesNotAdvanceCurrentTime() {
        when(parser.parse(any(), any())).thenReturn(new AiTimeParseResult(
                false, TimeType.UNKNOWN, null, null, null,
                TimePrecision.AMBIGUOUS, 0.95, "3주 뒤", DurationScale.UNSPECIFIED));

        var result = interpreter.interpret("3주 뒤에 여행 갈 생각이에요.", now);

        assertThat(result.interpreter()).isEqualTo(Interpreter.NONE);
        assertThat(result.timeChange()).isNull();
    }

    @Test
    void ordinaryActionHasNoTimeInterpretation() {
        var result = interpreter.interpret("*고개를 끄덕였다.*", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.NONE);
        assertThat(result.precision()).isNull();
        assertThat(result.timeChange()).isNull();
        verifyNoInteractions(parser);
    }

    @Test
    void unmatchedAiReturnsNone() {
        when(parser.parse(any(), any())).thenReturn(new AiTimeParseResult(
                false, TimeType.UNKNOWN, null, null, null, TimePrecision.AMBIGUOUS, 0.0, null, DurationScale.UNSPECIFIED));
        var result = interpreter.interpret("시간 이야기를 했다.", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.NONE);
        assertThat(result.precision()).isNull();
    }

    @Test
    void nextDayBaselineUsesResolvedTargetNotAlwaysTwentyFourHours() {
        var rule = interpreter.interpret("다음날 아침", now);
        assertThat(rule.timeChange().expectedElapsedMinutes()).isEqualTo(1200L);
        assertThat(rule.timeChange().appliedElapsedMinutes()).isEqualTo(1200L);
        var ai = resolver.resolve(new AiTimeParseResult(true, TimeType.NEXT_DAY,
                null, null, DayPart.MORNING, TimePrecision.EXACT, 0.95, "다음날 아침", DurationScale.UNSPECIFIED), now);
        assertThat(ai).isEqualTo(rule.timeChange());
    }

    @Test
    void pastDayPartRetainsBaselineButDoesNotRewindTime() {
        var change = resolver.resolve(new AiTimeParseResult(true, TimeType.SAME_DAY_PART,
                null, null, DayPart.MORNING, TimePrecision.EXACT, 0.95, "아침", DurationScale.UNSPECIFIED), now);
        assertThat(change.expectedElapsedMinutes()).isEqualTo(-240L);
        assertThat(change.appliedElapsedMinutes()).isZero();
        assertThat(change.changed()).isFalse();
    }

    private AiTimeParseResult relative(TimePrecision precision, double confidence) {
        return new AiTimeParseResult(true, TimeType.RELATIVE, 1L, TimeUnit.HOUR,
                null, precision, confidence, "한 시간 정도", DurationScale.UNSPECIFIED);
    }

    @Test
    void ambiguousScalesStayWithinServerBoundsAndAreRepeatable() {
        for (DurationScale scale : new DurationScale[] {
                DurationScale.SHORT, DurationScale.MEDIUM, DurationScale.LONG}) {
            long min = scale == DurationScale.SHORT ? 1 : scale == DurationScale.MEDIUM ? 10 : 30;
            long max = scale == DurationScale.SHORT ? 5 : scale == DurationScale.MEDIUM ? 30 : 90;
            for (int i = 0; i < 100; i++) {
                var current = now.plusMinutes(i);
                var parsed = ambiguous(scale, 0.95);
                var change = resolver.resolve(parsed, current);
                assertThat(change.changed()).isTrue();
                assertThat(change.expectedElapsedMinutes()).isNull();
                assertThat(change.appliedElapsedMinutes()).isBetween(min, max);
                assertThat(change.newTime()).isEqualTo(current.plusMinutes(change.appliedElapsedMinutes()));
                assertThat(resolver.resolve(parsed, current)).isEqualTo(change);
            }
        }
    }

    @Test
    void ambiguousInterpretationCarriesScaleAndSource() {
        when(parser.parse(any(), any())).thenReturn(ambiguous(DurationScale.LONG, 0.95));
        var result = interpreter.interpret("열심히 일했다. 한참 시간이 흐른 뒤 시계를 확인했다.", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.AI);
        assertThat(result.precision()).isEqualTo(TimePrecision.AMBIGUOUS);
        assertThat(result.durationScale()).isEqualTo(DurationScale.LONG);
        assertThat(result.timeChange().sourceExpression()).isEqualTo("한참 시간이 흐른 뒤");
        assertThat(result.timeChange().appliedElapsedMinutes()).isBetween(30L, 90L);
    }

    @Test
    void ambiguousRejectionRetainsScaleButAppliesNoTime() {
        when(parser.parse(any(), any())).thenReturn(ambiguous(DurationScale.LONG, 0.5));
        var result = interpreter.interpret("한참 시간이 흐른 뒤", now);
        assertThat(result.durationScale()).isEqualTo(DurationScale.LONG);
        assertThat(result.timeChange().changed()).isFalse();
        assertThat(result.timeChange().expectedElapsedMinutes()).isNull();
        assertThat(result.timeChange().appliedElapsedMinutes()).isZero();
        assertThat(resolver.resolve(ambiguous(DurationScale.LONG, Double.NaN), now).changed()).isFalse();
    }

    @Test
    void missingScaleDoesNotInventProgression() {
        var change = resolver.resolve(ambiguous(null, 0.95), now);
        assertThat(change.changed()).isFalse();
        assertThat(change.appliedElapsedMinutes()).isZero();
    }

    @Test
    void ambiguousAiAmountCannotOverrideServerPolicy() {
        var parsed = new AiTimeParseResult(true, TimeType.RELATIVE, 999L, TimeUnit.DAY,
                null, TimePrecision.AMBIGUOUS, 0.95, "한참", DurationScale.LONG);
        var change = resolver.resolve(parsed, now);
        assertThat(change.expectedElapsedMinutes()).isNull();
        assertThat(change.appliedElapsedMinutes()).isBetween(30L, 90L);
    }

    @Test
    void planRejectedByParserDoesNotApplyScale() {
        when(parser.parse(any(), any())).thenReturn(new AiTimeParseResult(false,
                TimeType.UNKNOWN, null, null, null, TimePrecision.AMBIGUOUS,
                0.95, "한참", DurationScale.LONG));
        var result = interpreter.interpret("나중에 한참 일해야겠다.", now);
        assertThat(result.interpreter()).isEqualTo(Interpreter.NONE);
        assertThat(result.durationScale()).isNull();
        assertThat(result.timeChange()).isNull();
    }

    private AiTimeParseResult ambiguous(DurationScale scale, double confidence) {
        return new AiTimeParseResult(true, TimeType.RELATIVE, null, null, null,
                TimePrecision.AMBIGUOUS, confidence, "한참 시간이 흐른 뒤", scale);
    }
}
