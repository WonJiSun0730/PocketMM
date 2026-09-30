package com.example.projectmm.domain.world;

public enum WorldLoreCategory {
    ERA_REGION_BACKGROUND(2000, 20),
    SOCIETY_CULTURE(500, 10),
    RACE_ORGANIZATION(1500, 10),
    WORLD_RULE(1000, 15),
    TERMINOLOGY(500, 20),
    HISTORY(1000, 10);

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
