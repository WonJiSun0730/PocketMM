package com.example.projectmm.application.world;

import com.example.projectmm.api.world.WorldLoreItem;
import com.example.projectmm.domain.world.WorldLoreCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorldLorePolicy {

    public static final int MAX_TOTAL_CHARACTERS = 10_000;

    public void validateWorldTotal(int otherCharacters, List<WorldLoreItem> entries) {
        long total = (long) otherCharacters + entries.stream()
                .mapToLong(entry -> length(entry.title()) + length(entry.content())).sum();
        if (total > MAX_TOTAL_CHARACTERS) {
            throw new IllegalArgumentException("세계 설정은 모든 항목 합계 10,000자까지 저장할 수 있어요.");
        }
    }

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

        if (totalCharacters > MAX_TOTAL_CHARACTERS) {
            throw new IllegalArgumentException(
                    category + " lore entries must not exceed "
                            + MAX_TOTAL_CHARACTERS
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
