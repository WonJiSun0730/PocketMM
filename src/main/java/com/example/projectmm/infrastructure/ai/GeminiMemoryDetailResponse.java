package com.example.projectmm.infrastructure.ai;

public record GeminiMemoryDetailResponse(
        String text,
        String salience,
        boolean protectedAnchor
) {
}