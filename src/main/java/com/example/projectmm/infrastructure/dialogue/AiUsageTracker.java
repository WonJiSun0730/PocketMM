package com.example.projectmm.infrastructure.dialogue;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiUsageTracker {

    private static final ThreadLocal<CaptureSlot> capture = new ThreadLocal<>();
    private static class CaptureSlot { final Long sessionId; Snapshot snapshot; CaptureSlot(Long id) {sessionId=id;} }
    public record Captured<T>(T value, Snapshot usage) {}
    public static <T> Captured<T> capture(Long sessionId, java.util.function.Supplier<T> work) {
        var previous=capture.get();var slot=new CaptureSlot(sessionId);capture.set(slot);
        try {return new Captured<>(work.get(),slot.snapshot);}
        finally {if(previous==null)capture.remove();else capture.set(previous);}
    }
    private final ConcurrentHashMap<Long, Snapshot> latest = new ConcurrentHashMap<>();

    public void record(
            Long sessionId,
            String provider,
            String model,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long totalTokens
    ) {
        if (sessionId == null) return;
        var snapshot = new Snapshot(
                provider, model, inputTokens, cachedInputTokens,
                outputTokens, totalTokens, Instant.now()
        );
        latest.put(sessionId,snapshot);
        var slot=capture.get();
        if(slot!=null && java.util.Objects.equals(slot.sessionId,sessionId))slot.snapshot=snapshot;
    }

    public Snapshot latest(Long sessionId) {
        return latest.get(sessionId);
    }

    public record Snapshot(
            String provider,
            String model,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long totalTokens,
            Instant recordedAt
    ) {
        // Standard text-token estimate; excludes separately billed cache writes/tools/taxes.
        @com.fasterxml.jackson.annotation.JsonProperty
        public java.math.BigDecimal estimatedCostUsd() {
            if (!"OPENAI".equalsIgnoreCase(provider) || model == null) return null;
            String normalized=model.replaceFirst("-\\d{4}-\\d{2}-\\d{2}$", "");
            String[] rates=switch(normalized) {
                case "gpt-5.6-luna" -> new String[]{"0.20","0.02","1.20"};
                case "gpt-5.6-terra" -> new String[]{"2.00","0.20","12.00"};
                case "gpt-5.6-sol", "gpt-5.6" -> new String[]{"4.00","0.40","20.00"};
                default -> null;
            };
            if (rates == null) return null;
            long input=Math.max(0,inputTokens), cached=Math.min(input,Math.max(0,cachedInputTokens));
            var inputMultiplier=input>272000?java.math.BigDecimal.valueOf(2):java.math.BigDecimal.ONE;
            var outputMultiplier=input>272000?new java.math.BigDecimal("1.5"):java.math.BigDecimal.ONE;
            return java.math.BigDecimal.valueOf(input-cached).multiply(new java.math.BigDecimal(rates[0]))
                .add(java.math.BigDecimal.valueOf(cached).multiply(new java.math.BigDecimal(rates[1])))
                .multiply(inputMultiplier)
                .add(java.math.BigDecimal.valueOf(Math.max(0,outputTokens)).multiply(new java.math.BigDecimal(rates[2])).multiply(outputMultiplier))
                .movePointLeft(6);
        }
    }
}
