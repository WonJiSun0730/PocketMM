package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;

public record ChatRequest(
        String message,
        ContentMode contentMode
) {
}