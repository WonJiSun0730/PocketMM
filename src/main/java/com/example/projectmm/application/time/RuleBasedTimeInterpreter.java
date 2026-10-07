package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.TimeChange;
import com.example.projectmm.domain.time.AiTimeParseResult.TimePrecision;
import com.example.projectmm.domain.time.TimeInterpretation;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedTimeInterpreter implements TimeInterpreter {

    private static final Pattern RELATIVE_TIME_PATTERN =
            Pattern.compile(
                    "(?:한\\s*)?"
                            + "([0-9]+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|열한|열두)"
                            + "\\s*(분|시간|일)"
                            + "\\s*(?:정도|쯤|가량|은|는|이)?"
                            + "\\s*(?:뒤|후|지났|지난|흘렀|흐른)"
            );

    private static final Pattern ELAPSED_DURATION_PATTERN =
            Pattern.compile(
                    "([0-9]+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|열한|열두)"
                            + "\\s*(분|시간|일|주|달|개월)"
                            + "\\s*(?:정도|쯤|가량)?"
                            + "(?=[^.!?\\n]{0,40}"
                            + "(?:했|하였다|지냈|보냈|살았|머물렀|계속했|이어졌|지속됐|지속되었|흘렀|지났))"
            );

    private static final Pattern APPROXIMATE_TIME_PATTERN =
            Pattern.compile(
                    "(정도|쯤|가량|"
                            + "것\\s*같|듯|"
                            + "몇\\s*시간|"
                            + "한참|얼마간|꽤\\s*오래)"
            );

    private static final Pattern NEXT_DAY_PATTERN =
            Pattern.compile(
                    "(?:다음\\s*날|다음날|이튿날)(?:\\s*(아침|오전|점심|낮|오후|저녁|밤))?"
            );

    @Override
    public TimeInterpretation interpret(
            String userInput,
            LocalDateTime currentTime
    ) {
        if (userInput == null || userInput.isBlank()) {
            return TimeInterpretation.noMatch();
        }

        Matcher elapsedDurationMatcher =
                ELAPSED_DURATION_PATTERN.matcher(userInput);

        if (elapsedDurationMatcher.find()) {
            long amount =
                    parseAmount(elapsedDurationMatcher.group(1));

            String unit =
                    elapsedDurationMatcher.group(2);

            LocalDateTime newTime =
                    addDuration(currentTime, amount, unit);

            return TimeInterpretation.matched(
                    TimeInterpretation.Interpreter.RULE,
                    TimePrecision.EXACT,
                    TimeChange.changed(
                            currentTime,
                            newTime,
                            elapsedDurationMatcher.group()
                    )
            );
        }

        if (APPROXIMATE_TIME_PATTERN.matcher(userInput).find()) {
            return TimeInterpretation.noMatch();
        }

        Matcher nextDayMatcher =
                NEXT_DAY_PATTERN.matcher(userInput);

        if (nextDayMatcher.find()) {

            LocalDateTime newTime =
                    currentTime.plusDays(1);

            String dayPart =
                    nextDayMatcher.group(1);

            if (dayPart != null) {
                newTime =
                        switch (dayPart) {
                            case "아침", "오전" ->
                                    newTime.withHour(8).withMinute(0).withSecond(0).withNano(0);

                            case "점심" ->
                                    newTime.withHour(12).withMinute(0).withSecond(0).withNano(0);

                            case "낮", "오후" ->
                                    newTime.withHour(15).withMinute(0).withSecond(0).withNano(0);

                            case "저녁" ->
                                    newTime.withHour(19).withMinute(0).withSecond(0).withNano(0);

                            case "밤" ->
                                    newTime.withHour(22).withMinute(0).withSecond(0).withNano(0);

                            default ->
                                    newTime;
                        };
            }

            return TimeInterpretation.matched(
                    TimeInterpretation.Interpreter.RULE,
                    TimePrecision.EXACT,
                    TimeChange.changed(
                            currentTime,
                            newTime,
                            nextDayMatcher.group()
                    )
            );
        }

        Matcher matcher =
                RELATIVE_TIME_PATTERN.matcher(userInput);

        if (!matcher.find()) {
            return TimeInterpretation.noMatch();
        }

        long amount =
                parseAmount(matcher.group(1));

        String unit =
                matcher.group(2);

        LocalDateTime newTime =
                addDuration(currentTime, amount, unit);

        return TimeInterpretation.matched(
                TimeInterpretation.Interpreter.RULE,
                TimePrecision.EXACT,
                TimeChange.changed(
                        currentTime,
                        newTime,
                        matcher.group()
                )
        );
    }

    private LocalDateTime addDuration(
            LocalDateTime currentTime,
            long amount,
            String unit
    ) {
        return switch (unit) {
            case "분" ->
                    currentTime.plusMinutes(amount);

            case "시간" ->
                    currentTime.plusHours(amount);

            case "일" ->
                    currentTime.plusDays(amount);

            case "주" ->
                    currentTime.plusWeeks(amount);

            case "달", "개월" ->
                    currentTime.plusMonths(amount);

            default ->
                    currentTime;
        };
    }

    private long parseAmount(String value) {
        return switch (value) {
            case "한" -> 1;
            case "두" -> 2;
            case "세" -> 3;
            case "네" -> 4;
            case "다섯" -> 5;
            case "여섯" -> 6;
            case "일곱" -> 7;
            case "여덟" -> 8;
            case "아홉" -> 9;
            case "열" -> 10;
            case "열한" -> 11;
            case "열두" -> 12;
            default -> Long.parseLong(value);
        };
    }
}
