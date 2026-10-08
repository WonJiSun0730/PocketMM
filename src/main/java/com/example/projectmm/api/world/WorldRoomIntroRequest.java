package com.example.projectmm.api.world;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record WorldRoomIntroRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank String content,
        Integer sortOrder,
        LocalDateTime initialSimulatedAt,
        @Size(max = 200) String initialLocation,
        List<@Size(max = 300) String> initialConditions,
        List<@Size(max = 300) String> initialInventory
) {}
