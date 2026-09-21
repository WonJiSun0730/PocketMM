package com.example.projectmm.domain.memory;

public record MemoryDecayResult(
        int originalStrength,
        int effectiveStrength,
        boolean compressionRequired
) {
}