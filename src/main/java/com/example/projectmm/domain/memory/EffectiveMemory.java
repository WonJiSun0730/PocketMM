package com.example.projectmm.domain.memory;

import java.time.LocalDateTime;

public record EffectiveMemory(
        Long memoryId,
        String summary,
        int importance,
        int effectiveStrength,
        LocalDateTime rememberedAt,
        boolean compressionRequired,
        String evidenceQuote,
        MemoryFact fact
) {
    public EffectiveMemory(Long memoryId, String summary, int importance, int effectiveStrength,
                           LocalDateTime rememberedAt, boolean compressionRequired) {
        this(memoryId, summary, importance, effectiveStrength, rememberedAt, compressionRequired, null, null);
    }
}
