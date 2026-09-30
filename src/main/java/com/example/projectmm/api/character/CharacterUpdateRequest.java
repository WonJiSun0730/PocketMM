package com.example.projectmm.api.character;

import java.util.List;

public record CharacterUpdateRequest(
        String description,
        String rawDescription,
        String playGuide,
        List<String> examples,
        String exampleDialogue,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}