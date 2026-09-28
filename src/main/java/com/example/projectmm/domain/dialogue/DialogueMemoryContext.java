package com.example.projectmm.domain.dialogue;

import java.time.LocalDateTime;

public record DialogueMemoryContext(
        String summary,
        int importance,
        int strength,
        LocalDateTime rememberedAt,
        String evidenceQuote,
        com.example.projectmm.domain.memory.MemoryFact fact
) {
    public DialogueMemoryContext(String summary, int importance, int strength, LocalDateTime rememberedAt) {
        this(summary, importance, strength, rememberedAt, null, null);
    }
}
