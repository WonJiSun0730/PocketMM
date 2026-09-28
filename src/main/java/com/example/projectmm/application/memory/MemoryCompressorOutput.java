package com.example.projectmm.application.memory;

import java.util.List;

/** Evidence IDs supplied to the successful compression, not unfiltered candidates or model citations. */
public record MemoryCompressorOutput(String summary, List<Long> evidenceMemoryIds) {
    public MemoryCompressorOutput {
        if (evidenceMemoryIds == null || evidenceMemoryIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("evidence IDs must be persisted memory IDs");
        }
        evidenceMemoryIds = evidenceMemoryIds.stream().distinct().toList();
    }
}
