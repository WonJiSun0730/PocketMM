package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.MemoryDetailSalience;

public record MemoryDetailCandidate(
        String text,
        MemoryDetailSalience salience,
        boolean protectedAnchor
) {
}