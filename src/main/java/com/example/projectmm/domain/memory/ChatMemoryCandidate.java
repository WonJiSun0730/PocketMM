package com.example.projectmm.domain.memory;

import com.example.projectmm.application.memory.MemoryDetailCandidate;

import java.util.List;

public record ChatMemoryCandidate(
        String quote,
        int importance,
        MemoryFact fact,
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
            MemoryFact fact
    ) {
        this(
                quote,
                importance,
                fact,
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
                List.of()
        );
    }
}