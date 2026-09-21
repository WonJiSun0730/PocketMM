package com.example.projectmm.domain.ai;

import java.time.LocalDateTime;

public record AiMemoryContext(
        String summary,
        int importance,
        int strength,
        LocalDateTime rememberedAt
) {
}