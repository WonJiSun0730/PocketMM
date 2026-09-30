package com.example.projectmm.application.world;

import com.example.projectmm.domain.world.WorldLoreCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorldLorePolicy {

    public List<String> normalizeAndValidate(WorldLoreCategory category, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<String> normalized = entries.stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .map(String::trim)
                .toList();

        if (normalized.size() > category.getMaxEntries()) {
            throw new IllegalArgumentException(
                    category + " lore entries must not exceed " + category.getMaxEntries() + "."
            );
        }

        int totalCharacters = normalized.stream()
                .mapToInt(String::length)
                .sum();

        if (totalCharacters > category.getMaxTotalCharacters()) {
            throw new IllegalArgumentException(
                    category + " lore entries must not exceed "
                            + category.getMaxTotalCharacters()
                            + " characters in total."
            );
        }

        return normalized;
    }
}
