package com.example.projectmm.api.world;

import jakarta.validation.constraints.NotBlank;

public record WorldRoomChatRequest(
        @NotBlank String message
) {}
