package com.example.projectmm.domain.dialogue;

import java.time.LocalDateTime;

public record DialogueMemoryContext(
        String summary,
        int importance,
        int strength,
        LocalDateTime rememberedAt
) {
}