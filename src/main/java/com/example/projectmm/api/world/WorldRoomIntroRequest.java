package com.example.projectmm.api.world;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorldRoomIntroRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank String content,
        Integer sortOrder
) {}
