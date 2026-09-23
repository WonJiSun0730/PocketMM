package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;

import java.time.LocalDateTime;

public record ChatResponse(
        Long characterId,
        String characterName,
        String message,
        ContentMode contentMode,
        LocalDateTime createdAt,
        ChatDebugInfo debug
) {
}