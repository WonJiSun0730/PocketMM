package com.example.projectmm.domain.memory;

public record MemoryDecision(
        boolean memorable,
        int strength
) {

    public static MemoryDecision remember(
            int strength
    ) {
        return new MemoryDecision(
                true,
                strength
        );
    }

    public static MemoryDecision ignore() {
        return new MemoryDecision(
                false,
                0
        );
    }
}