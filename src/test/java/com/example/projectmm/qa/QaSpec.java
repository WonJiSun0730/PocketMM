package com.example.projectmm.qa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Test classpath only. Prices are supplied by the operator, never inferred from model names. */
public record QaSpec(String runId, int turns, boolean paid, String approval,
                     Persona persona, String personaModel, String targetModel, String extractionModel,
                     BigDecimal budgetUsd, BigDecimal turnReservationUsd, Map<String, Rate> prices,
                     int recentTurns, boolean memoryEnabled) {
    public enum Kind { EVERYDAY, EMOTIONAL, EVENT, CORRECTION, MIXED }
    public record Persona(Kind kind, String personality, String speech, String relationship,
                          String tendencies, String goal) {}
    public record Rate(BigDecimal input, BigDecimal cached, BigDecimal written, BigDecimal output,
                       long largeContextThreshold, BigDecimal largeInputMultiplier,
                       BigDecimal largeOutputMultiplier, String source, String checkedAt) {
        public Rate {
            for (var n : List.of(input, cached, written, output, largeInputMultiplier, largeOutputMultiplier))
                if (n == null || n.signum() < 0) throw new IllegalArgumentException("Invalid price");
            if (source == null || source.isBlank() || checkedAt == null || checkedAt.isBlank())
                throw new IllegalArgumentException("Price source and verification date required");
        }
        public BigDecimal cost(long in, long read, Long write, long out) {
            if (write == null || in < 0 || read < 0 || write < 0 || out < 0 || read + write > in) return null;
            var im = largeContextThreshold > 0 && in > largeContextThreshold ? largeInputMultiplier : BigDecimal.ONE;
            var om = largeContextThreshold > 0 && in > largeContextThreshold ? largeOutputMultiplier : BigDecimal.ONE;
            return input.multiply(BigDecimal.valueOf(in-read-write)).add(cached.multiply(BigDecimal.valueOf(read)))
                    .add(written.multiply(BigDecimal.valueOf(write))).multiply(im)
                    .add(output.multiply(BigDecimal.valueOf(out)).multiply(om)).movePointLeft(6);
        }
    }
    public QaSpec {
        if (runId == null || !runId.matches("[a-zA-Z0-9_-]{1,60}") || turns < 1 || turns > 100_000
                || recentTurns < 1 || recentTurns > 6 || persona == null || persona.kind() == null
                || budgetUsd == null || turnReservationUsd == null || budgetUsd.signum() < 0
                || turnReservationUsd.signum() < 0) throw new IllegalArgumentException("Invalid QA configuration");
        prices = Map.copyOf(prices);
        if (paid) {
            if (!runId.equals(approval) || budgetUsd.signum() <= 0 || turnReservationUsd.signum() <= 0)
                throw new IllegalArgumentException("Paid run requires explicit run-id approval and positive budget/reservation");
            if (!"gpt-5.6-luna".equals(personaModel))
                throw new IllegalArgumentException("Paid persona bot must use gpt-5.6-luna; no automatic replacement");
            for (var model : List.of(personaModel, targetModel, extractionModel))
                if (model == null || model.isBlank() || !prices.containsKey(model))
                    throw new IllegalArgumentException("Each paid model requires configured pricing");
        }
    }
    public static int preset(String name) {
        return switch (name.toUpperCase()) { case "SMOKE" -> 20; case "SHORT" -> 100;
            case "MEDIUM" -> 500; case "LONG" -> 1000; default -> throw new IllegalArgumentException("Use custom turns"); };
    }
    public static QaSpec mock(String id, int turns) {
        return new QaSpec(id, turns, false, null,
                new Persona(Kind.MIXED,"차분하고 호기심 많음","짧고 자연스러운 한국어","최근 알게 된 동료",
                        "반응을 듣고 일상과 사건을 섞음","함께 시간을 보내며 신뢰 쌓기"),
                "mock-persona","mock-target","mock-extraction",BigDecimal.ZERO,BigDecimal.ZERO,Map.of(),3,true);
    }
}
