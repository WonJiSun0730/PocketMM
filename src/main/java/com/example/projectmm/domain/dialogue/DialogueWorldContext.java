package com.example.projectmm.domain.dialogue;

import java.util.List;

public record DialogueWorldContext(
        Long worldId,
        String worldName,
        String playGuide,
        List<DialogueWorldLore> lore,
        List<DialogueWorldSystem> systems
) {}
