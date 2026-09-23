package com.example.projectmm.api.character;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CharacterCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,
        String rawDescription,
        String playGuide,
        String intro,

        @Size(max = 10)
        List<String> examples,

        String exampleDialogue,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}