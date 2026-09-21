package com.example.projectmm.domain.memory;

public record MemoryCompressionResult(
        boolean compressed,
        String summary
) {

    public static MemoryCompressionResult compressed(
            String summary
    ) {
        return new MemoryCompressionResult(
                true,
                summary
        );
    }

    public static MemoryCompressionResult skipped(
            String summary
    ) {
        return new MemoryCompressionResult(
                false,
                summary
        );
    }
}