package com.example.projectmm.api.character;

import java.util.List;

public record CharacterResponse(
        Long id,
        String name,
        String description,
        String rawDescription,
        String playGuide,
        String intro,
        List<String> examples,
        String exampleDialogue,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}