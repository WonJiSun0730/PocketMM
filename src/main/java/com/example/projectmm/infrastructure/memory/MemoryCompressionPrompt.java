package com.example.projectmm.infrastructure.memory;

import java.util.List;

/** Text and evidence are captured together after the same access/scope/dedup filters. */
public record MemoryCompressionPrompt(String text, List<Long> evidenceMemoryIds) {
    public MemoryCompressionPrompt {
        evidenceMemoryIds = List.copyOf(evidenceMemoryIds);
    }
}
