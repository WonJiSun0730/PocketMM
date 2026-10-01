package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldConversation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorldRoomCreateRequest(
        @NotBlank @Size(max = 100) String title,
        WorldConversation.Visibility visibility,
        Long playerProfileId
) {}
