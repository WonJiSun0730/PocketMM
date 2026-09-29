package com.example.projectmm.infrastructure.ai;

public class GeminiDecisionParseException
        extends IllegalStateException {

    private final Reason reason;

    public GeminiDecisionParseException(
            Reason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public GeminiDecisionParseException(
            Reason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        EMPTY_RESPONSE,
        MISSING_CONTENT,
        MALFORMED_JSON,
        INVALID_STRUCTURE
    }
}