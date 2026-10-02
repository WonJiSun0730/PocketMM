package com.example.projectmm.application.memory;

import java.time.LocalDateTime;

public record PocketUserSpeechCommitted(
        Long sessionId,
        Long sourceMessageId,
        String userMessage,
        LocalDateTime at
) {
}
