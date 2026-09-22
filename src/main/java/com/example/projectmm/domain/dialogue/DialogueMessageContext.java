package com.example.projectmm.domain.dialogue;

import com.example.projectmm.domain.chat.ChatRole;

import java.time.LocalDateTime;

public record DialogueMessageContext(
        ChatRole role,
        String content,
        LocalDateTime createdAt
) {
}