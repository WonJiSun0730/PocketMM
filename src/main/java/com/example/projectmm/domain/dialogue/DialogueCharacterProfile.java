package com.example.projectmm.domain.dialogue;

import java.util.List;

public record DialogueCharacterProfile(
        String description,
        String rawDescription,
        List<String> examples,
        String personality,
        String speechStyle,
        String background,
        String appearance
) {
}