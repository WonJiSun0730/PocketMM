package com.example.projectmm.domain.world;

public enum WorldLoreCategory {
    ERA_REGION_BACKGROUND(10000, 20),
    SOCIETY_CULTURE(10000, 10),
    RACE_ORGANIZATION(10000, 10),
    WORLD_RULE(10000, 15),
    TERMINOLOGY(10000, 20),
    HISTORY(10000, 10);

    private final int maxTotalCharacters;
    private final int maxEntries;

    WorldLoreCategory(int maxTotalCharacters, int maxEntries) {
        this.maxTotalCharacters = maxTotalCharacters;
        this.maxEntries = maxEntries;
    }

    public int getMaxTotalCharacters() {
        return maxTotalCharacters;
    }

    public int getMaxEntries() {
        return maxEntries;
    }
}
