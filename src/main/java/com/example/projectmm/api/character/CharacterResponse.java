package com.example.projectmm.api.character;

public record CharacterResponse(
        Long id,
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