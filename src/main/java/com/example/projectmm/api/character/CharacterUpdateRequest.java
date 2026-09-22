package com.example.projectmm.api.character;

public record CharacterUpdateRequest(
        String description,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}