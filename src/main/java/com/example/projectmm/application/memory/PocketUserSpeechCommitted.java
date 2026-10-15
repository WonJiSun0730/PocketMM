package com.example.projectmm.application.memory;

import java.time.LocalDateTime;

public record PocketUserSpeechCommitted(
        Long sessionId,
        Long sourceMessageId,
        String userMessage,
        LocalDateTime at,
        java.util.List<Long> audienceCharacterIds
) {
    public PocketUserSpeechCommitted(Long sessionId, Long sourceMessageId, String userMessage, LocalDateTime at) {
        this(sessionId, sourceMessageId, userMessage, at, java.util.List.of());
    }
}

