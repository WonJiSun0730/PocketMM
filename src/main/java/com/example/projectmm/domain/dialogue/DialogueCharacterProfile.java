package com.example.projectmm.domain.dialogue;

public record DialogueCharacterProfile(
        String description,
        String rawDescription,
        String exampleDialogue,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}