package com.example.projectmm.domain.simulation;

public record StateDelta(
        int energyChange,
        int moodChange,
        int memoryAbilityChange
) {

    public StateDelta(
            int energyChange,
            int moodChange
    ) {
        this(
                energyChange,
                moodChange,
                0
        );
    }

    public static StateDelta none() {
        return new StateDelta(
                0,
                0,
                0
        );
    }
}