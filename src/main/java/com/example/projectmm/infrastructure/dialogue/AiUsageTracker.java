package com.example.projectmm.infrastructure.dialogue;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiUsageTracker {

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
        latest.put(sessionId, new Snapshot(
                provider, model, inputTokens, cachedInputTokens,
                outputTokens, totalTokens, Instant.now()
        ));
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
    ) {}
}
