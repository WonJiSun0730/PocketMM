package com.example.projectmm.domain.memory;

import com.example.projectmm.application.memory.MemoryDetailCandidate;

import java.util.List;

public record ChatMemoryCandidate(
        String quote,
        int importance,
        MemoryFact fact,
        SemanticMemory semanticMemory,
        List<MemoryDetailCandidate> details
) {

    public ChatMemoryCandidate {
        details = details == null
                ? List.of()
                : List.copyOf(details);
    }

    public ChatMemoryCandidate(
            String quote,
            int importance,
            MemoryFact fact,
            List<MemoryDetailCandidate> details
    ) {
        this(
                quote,
                importance,
                fact,
                null,
                details
        );
    }

    public ChatMemoryCandidate(
            String quote,
            int importance,
            MemoryFact fact
    ) {
        this(
                quote,
                importance,
                fact,
                null,
                List.of()
        );
    }

    public ChatMemoryCandidate(
            String quote,
            int importance
    ) {
        this(
                quote,
                importance,
                null,
                null,
                List.of()
        );
    }
}