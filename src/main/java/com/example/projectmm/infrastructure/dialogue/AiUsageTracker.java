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
    private record Scope(Long sessionId, String purpose) {}
    private static final ThreadLocal<Scope> scope = new ThreadLocal<>();
    public static Long currentSessionId() {return scope.get()==null?null:scope.get().sessionId();}
    public static <T> T withScope(Long sessionId, String purpose, java.util.function.Supplier<T> work) {
        var previous=scope.get();scope.set(new Scope(sessionId,purpose));
        try {return work.get();} finally {if(previous==null)scope.remove();else scope.set(previous);}
    }
    public static <T> T withPurpose(Long sessionId, String purpose, java.util.function.Supplier<T> work) {
        return withScope(sessionId,purpose,work);
    }
    public record Call(String purpose, Snapshot usage, String status) {
        public Call(String purpose, Snapshot usage) {this(purpose,usage,"USAGE_REPORTED");}
    }
    public <T> T accountRequest(Long sessionId,String provider,String model,java.util.function.Supplier<T> request) {
        try {return request.get();}
        catch(RuntimeException ex) {recordUnavailable(sessionId,provider,model,"REQUEST_FAILED");throw ex;}
    }
    public void recordUnavailable(Long sessionId,String provider,String model,String reason) {
        if(sessionId==null)return;
        String purpose=scope.get()==null?"ROLEPLAY":scope.get().purpose();
        calls.compute(sessionId,(id,old)->{
            var next=new java.util.ArrayList<Call>(old==null?java.util.List.of():old);
            next.add(new Call(purpose,null,reason));
            return java.util.List.copyOf(next.subList(Math.max(0,next.size()-1000),next.size()));
        });
        log.warn("Pocket AI usage unavailable session={} purpose={} provider={} model={} reason={}",sessionId,purpose,provider,model,reason);
    }
    public record PromptDiagnostics(String model, String cacheMode, String stableHash, int stableCharacters,
                                    String dynamicHash, boolean sameStablePrefixAsPrevious) {}
    private final ConcurrentHashMap<Long, java.util.List<Call>> calls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PromptDiagnostics> prompts = new ConcurrentHashMap<>();
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AiUsageTracker.class);
    public java.util.List<Call> calls(Long sessionId) {return calls.getOrDefault(sessionId,java.util.List.of());}
    public PromptDiagnostics promptDiagnostics(Long sessionId) {return prompts.get(sessionId);}
    public void recordPromptDiagnostics(Long sessionId, String model, String stable, String dynamic) {
        if(sessionId==null)return;
        String hash=hash(stable);var previous=prompts.get(sessionId);
        var value=new PromptDiagnostics(model,"explicit",hash,stable.length(),hash(dynamic),
                previous!=null && model.equals(previous.model()) && hash.equals(previous.stableHash()));
        prompts.put(sessionId,value);
        log.info("Pocket AI prompt session={} diagnostics={}",sessionId,value);
    }
    private static String hash(String text) {
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
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
        record(sessionId, provider, model, inputTokens, cachedInputTokens, null, outputTokens, totalTokens);
    }

    public void record(Long sessionId, String provider, String model, long inputTokens,
                       long cachedInputTokens, Long cacheWriteTokens, long outputTokens, long totalTokens) {
        record(sessionId, provider, model, inputTokens, cachedInputTokens, cacheWriteTokens,
                outputTokens, totalTokens, null);
    }

    public void record(Long sessionId, String provider, String model, long inputTokens,
                       long cachedInputTokens, Long cacheWriteTokens, long outputTokens, long totalTokens,
                       PromptTokenBreakdown breakdown) {
        if (sessionId == null) return;
        var snapshot = new Snapshot(
                provider, model, inputTokens, cachedInputTokens,
                cacheWriteTokens, outputTokens, totalTokens, Instant.now(), breakdown
        );
        String purpose=scope.get()==null?"ROLEPLAY":scope.get().purpose();
        calls.compute(sessionId,(id,old)->{
            var next=new java.util.ArrayList<Call>(old==null?java.util.List.of():old);
            next.add(new Call(purpose,snapshot));
            return java.util.List.copyOf(next.subList(Math.max(0,next.size()-1000),next.size()));
        });
        log.info("Pocket AI usage session={} purpose={} usage={}",sessionId,purpose,snapshot);
        if (!"ROLEPLAY".equals(purpose)) return;
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
            Long cacheWriteTokens,
            long outputTokens,
            long totalTokens,
            Instant recordedAt,
            PromptTokenBreakdown promptTokenEstimate
    ) {
        public Snapshot(String provider, String model, long inputTokens, long cachedInputTokens,
                        Long cacheWriteTokens, long outputTokens, long totalTokens, Instant recordedAt) {
            this(provider, model, inputTokens, cachedInputTokens, cacheWriteTokens, outputTokens,
                    totalTokens, recordedAt, null);
        }
        @com.fasterxml.jackson.annotation.JsonProperty
        public Long ordinaryInputTokens() {
            if (cacheWriteTokens == null || inputTokens < 0 || cachedInputTokens < 0
                    || cachedInputTokens > inputTokens || cacheWriteTokens < 0
                    || cacheWriteTokens > inputTokens-cachedInputTokens) return null;
            return inputTokens - cachedInputTokens - cacheWriteTokens;
        }

        // Standard text-token estimate; tools, service-tier uplifts and taxes excluded.
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
            // Missing or inconsistent cache-write usage cannot support a complete cost estimate.
            if (ordinaryInputTokens() == null) return null;
            long written=cacheWriteTokens;
            var inputMultiplier=input>272000?java.math.BigDecimal.valueOf(2):java.math.BigDecimal.ONE;
            var outputMultiplier=input>272000?new java.math.BigDecimal("1.5"):java.math.BigDecimal.ONE;
            return java.math.BigDecimal.valueOf(input-cached-written).multiply(new java.math.BigDecimal(rates[0]))
                .add(java.math.BigDecimal.valueOf(cached).multiply(new java.math.BigDecimal(rates[1])))
                .add(java.math.BigDecimal.valueOf(written).multiply(new java.math.BigDecimal(rates[0])).multiply(new java.math.BigDecimal("1.25")))
                .multiply(inputMultiplier)
                .add(java.math.BigDecimal.valueOf(Math.max(0,outputTokens)).multiply(new java.math.BigDecimal(rates[2])).multiply(outputMultiplier))
                .movePointLeft(6);
        }
    }
}
