package com.example.projectmm.domain.time;

public record TimeInterpretation(
        Status status,
        TimeChange timeChange
) {

    public enum Status {
        MATCHED,
        NO_MATCH
    }

    public static TimeInterpretation matched(TimeChange timeChange) {
        return new TimeInterpretation(Status.MATCHED, timeChange);
    }

    public static TimeInterpretation noMatch() {
        return new TimeInterpretation(Status.NO_MATCH, null);
    }

    public boolean matched() {
        return status == Status.MATCHED;
    }
}