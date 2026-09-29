package com.example.projectmm.domain.memory;

public enum MemoryDetailSalience {

    LOW(1),
    NORMAL(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    MemoryDetailSalience(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    public boolean protectedByDefault() {
        return this == HIGH
                || this == CRITICAL;
    }
}