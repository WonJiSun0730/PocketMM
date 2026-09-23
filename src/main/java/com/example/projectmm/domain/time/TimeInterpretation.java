package com.example.projectmm.domain.time;

public record TimeInterpretation(
        Status status,
        Interpreter interpreter,
        AiTimeParseResult.TimePrecision precision,
        AiTimeParseResult.DurationScale durationScale,
        TimeChange timeChange
) {

    public enum Status {
        MATCHED,
        NO_MATCH
    }

    public enum Interpreter {
        RULE, AI, NONE
    }

    public static TimeInterpretation matched(
            Interpreter interpreter,
            AiTimeParseResult.TimePrecision precision,
            TimeChange timeChange
    ) {
        return matched(interpreter, precision, null, timeChange);
    }

    public static TimeInterpretation matched(
            Interpreter interpreter,
            AiTimeParseResult.TimePrecision precision,
            AiTimeParseResult.DurationScale durationScale,
            TimeChange timeChange
    ) {
        return new TimeInterpretation(Status.MATCHED, interpreter, precision, durationScale, timeChange);
    }

    public static TimeInterpretation noMatch() {
        return new TimeInterpretation(Status.NO_MATCH, Interpreter.NONE, null, null, null);
    }

    public boolean matched() {
        return status == Status.MATCHED;
    }
}
