package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.chat.ChatRole;

import java.time.LocalDateTime;

public record ChatHistoryResponse(
        Long id,
        ChatRole role,
        String message,
        ContentMode contentMode,
        LocalDateTime createdAt
) {
}