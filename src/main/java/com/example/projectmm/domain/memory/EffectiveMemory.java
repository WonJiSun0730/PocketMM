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
        MemoryFact fact,
        SemanticMemory semanticMemory,
        MemoryAccessLevel accessLevel,
        MemoryTier tier
) {

    public EffectiveMemory(
            Long memoryId,
            String summary,
            int importance,
            int effectiveStrength,
            LocalDateTime rememberedAt,
            boolean compressionRequired
    ) {
        this(
                memoryId,
                summary,
                importance,
                effectiveStrength,
                rememberedAt,
                compressionRequired,
                null,
                null,
                null,
                MemoryAccessLevel.EXPLICIT,
                MemoryTier.WARM
        );
    }

    public EffectiveMemory(
            Long memoryId,
            String summary,
            int importance,
            int effectiveStrength,
            LocalDateTime rememberedAt,
            boolean compressionRequired,
            String evidenceQuote,
            MemoryFact fact
    ) {
        this(
                memoryId,
                summary,
                importance,
                effectiveStrength,
                rememberedAt,
                compressionRequired,
                evidenceQuote,
                fact,
                null,
                MemoryAccessLevel.EXPLICIT,
                MemoryTier.WARM
        );
    }

    public EffectiveMemory(
            Long memoryId,
            String summary,
            int importance,
            int effectiveStrength,
            LocalDateTime rememberedAt,
            boolean compressionRequired,
            String evidenceQuote,
            MemoryFact fact,
            MemoryAccessLevel accessLevel
    ) {
        this(
                memoryId,
                summary,
                importance,
                effectiveStrength,
                rememberedAt,
                compressionRequired,
                evidenceQuote,
                fact,
                null,
                accessLevel,
                MemoryTier.WARM
        );
    }

    public EffectiveMemory(
            Long memoryId,
            String summary,
            int importance,
            int effectiveStrength,
            LocalDateTime rememberedAt,
            boolean compressionRequired,
            String evidenceQuote,
            MemoryFact fact,
            MemoryAccessLevel accessLevel,
            MemoryTier tier
    ) {
        this(
                memoryId,
                summary,
                importance,
                effectiveStrength,
                rememberedAt,
                compressionRequired,
                evidenceQuote,
                fact,
                null,
                accessLevel,
                tier
        );
    }
}