package com.example.projectmm.api.character;

public record CharacterUpdateRequest(
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