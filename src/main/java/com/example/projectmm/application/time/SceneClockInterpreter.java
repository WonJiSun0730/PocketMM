package com.example.projectmm.application.time;

import com.example.projectmm.domain.time.AiTimeParseResult.TimePrecision;
import com.example.projectmm.domain.time.TimeChange;
import com.example.projectmm.domain.time.TimeInterpretation;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** Only completed scene narration or a current clock observation, never an appointment. */
final class SceneClockInterpreter {
    private static final Pattern CLOCK = Pattern.compile(
            "(?<![0-9])(오전|오후|아침|저녁|밤)?\\s*([0-9]{1,2})"
                    + "(?:시(?:\\s*([0-9]{1,2})분|\\s*(반))?|:([0-9]{2}))\\s*"
                    + "(?:이다|입니다|였다|이었|였|다[.!*\\s]|가\\s*(?:되었|됐)|을\\s*가리|에\\s*(?:도착했|돌아왔))");
    private static final Pattern NON_SCENE = Pattern.compile(
            "(?:약속|예약|상담[은이가]|상담\\s*시간|예정|계획|내일|어제|기억|회상|만나기로|라면|다면|일\\s*전)");
    private static final Pattern DATE_JUMP = Pattern.compile("([0-9]+)\\s*일\\s*(?:후|뒤)");
    private static final Pattern NEXT_DAY = Pattern.compile("다음\\s*날|다음날|이튿날");

    static TimeInterpretation interpret(String input, LocalDateTime current) {
        var matcher = CLOCK.matcher(input);
        LocalDateTime target = null;
        String source = null;
        while (matcher.find()) {
            int boundary = Math.max(input.lastIndexOf('.', matcher.start()), input.lastIndexOf('\n', matcher.start()));
            boundary = Math.max(boundary, input.lastIndexOf('*', matcher.start()));
            String clause = input.substring(boundary + 1, matcher.start());
            if (NON_SCENE.matcher(clause).find()) continue;
            int hour = Integer.parseInt(matcher.group(2));
            int minute = matcher.group(3) != null ? Integer.parseInt(matcher.group(3))
                    : matcher.group(4) != null ? 30
                    : matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
            String part = matcher.group(1);
            if (minute > 59 || hour > 23 || (part != null && (hour < 1 || hour > 12))) continue;
            if (part != null) {
                hour = hour % 12 + (part.equals("오후") || part.equals("저녁") || part.equals("밤") ? 12 : 0);
            } else if (hour >= 1 && hour <= 12 && matcher.group(5) == null) {
                hour = hour % 12 + (current.getHour() >= 12 ? 12 : 0);
            }
            var date = current;
            var jump = DATE_JUMP.matcher(input.substring(0, matcher.start()));
            if (jump.find()) {
                long days = Long.parseLong(jump.group(1));
                if (days > 36500) continue;
                date = date.plusDays(days);
            } else if (NEXT_DAY.matcher(input.substring(0, matcher.start())).find()) {
                date = date.plusDays(1);
            } else if (current.getHour() >= 18 && hour < 6 && part != null) {
                date = date.plusDays(1);
            }
            target = date.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
            source = matcher.group();
        }
        if (target == null) return TimeInterpretation.noMatch();
        var change = target.equals(current) ? TimeChange.unchanged(current, source, 0L)
                : TimeChange.changed(current, target, source);
        return TimeInterpretation.matched(TimeInterpretation.Interpreter.RULE, TimePrecision.EXACT, change);
    }
}
