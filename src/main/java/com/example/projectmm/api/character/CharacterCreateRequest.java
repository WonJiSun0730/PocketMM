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

        @Size(max = 10)
        List<String> examples,

        String exampleDialogue,
        String personality,
        String speechStyle,
        String background,
        String appearance,
        @Size(max = 500) String modelUrl,
        Double modelHeight
) {
    public CharacterCreateRequest(
            String name,
            String description,
            String rawDescription,
            List<String> examples,
            String exampleDialogue,
            String personality,
            String speechStyle,
            String background,
            String appearance
    ) {
        this(name, description, rawDescription, examples, exampleDialogue, personality, speechStyle,
                background, appearance, null, null);
    }
}