package com.example.projectmm.api.world;

import com.example.projectmm.domain.world.WorldLoreCategory;

import java.util.List;

public record WorldLoreResponse(
        WorldLoreCategory category,
        List<String> entries,
        int totalCharacters,
        int maxTotalCharacters,
        int maxEntries
) {
}
