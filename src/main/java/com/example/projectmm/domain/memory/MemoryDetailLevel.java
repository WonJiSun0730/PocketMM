package com.example.projectmm.domain.memory;

public enum MemoryDetailLevel {

    DETAILED,
    SUMMARY,
    PATTERN,
    SCHEMA;

    public boolean canCompressFurther() {
        return this != SCHEMA;
    }

    public MemoryDetailLevel next() {
        return switch (this) {
            case DETAILED -> SUMMARY;
            case SUMMARY -> PATTERN;
            case PATTERN -> SCHEMA;
            case SCHEMA -> SCHEMA;
        };
    }
}