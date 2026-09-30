package com.example.projectmm.api.world;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorldUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        String description,
        @Size(max = 2000)
        String introduction
) {
}
