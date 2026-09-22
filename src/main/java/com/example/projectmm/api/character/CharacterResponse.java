package com.example.projectmm.api.character;

public record CharacterResponse(
        Long id,
        String name,
        String description,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}