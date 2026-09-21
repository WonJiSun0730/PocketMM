package com.example.projectmm.domain.simulation;

public record StateDelta(
        int energyChange,
        int moodChange
) {

    public static StateDelta none() {
        return new StateDelta(0, 0);
    }
}