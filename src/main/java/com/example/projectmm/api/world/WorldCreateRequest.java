package com.example.projectmm.api.world;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorldCreateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        String description
) {
}
