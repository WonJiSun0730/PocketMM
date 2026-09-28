package com.example.projectmm.domain.memory;

public record ChatMemoryCandidate(String quote, int importance, MemoryFact fact) {
    public ChatMemoryCandidate(String quote, int importance) { this(quote, importance, null); }
}
