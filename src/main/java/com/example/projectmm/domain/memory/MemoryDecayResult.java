package com.example.projectmm.domain.memory;

public record MemoryDecayResult(
        int originalStrength,
        int decayedStrength,
        int effectiveStrength,
        boolean compressionRequired
) {
}