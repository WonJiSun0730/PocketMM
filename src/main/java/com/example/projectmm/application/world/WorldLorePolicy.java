package com.example.projectmm.application.world;

import com.example.projectmm.api.world.WorldLoreItem;
import com.example.projectmm.domain.world.WorldLoreCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorldLorePolicy {

    public List<WorldLoreItem> normalizeAndValidate(WorldLoreCategory category, List<WorldLoreItem> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<WorldLoreItem> normalized = entries.stream()
                .filter(entry -> entry != null && (!isBlank(entry.title()) || !isBlank(entry.content())))
                .map(entry -> new WorldLoreItem(normalize(entry.title()), normalize(entry.content())))
                .toList();

        if (normalized.size() > category.getMaxEntries()) {
            throw new IllegalArgumentException(
                    category + " lore entries must not exceed " + category.getMaxEntries() + "."
            );
        }

        int totalCharacters = normalized.stream()
                .mapToInt(entry -> length(entry.title()) + length(entry.content()))
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
