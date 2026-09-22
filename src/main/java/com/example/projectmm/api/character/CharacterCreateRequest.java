package com.example.projectmm.api.character;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CharacterCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,
        String rawDescription,
        String playGuide,
        String intro,
        String exampleDialogue,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}