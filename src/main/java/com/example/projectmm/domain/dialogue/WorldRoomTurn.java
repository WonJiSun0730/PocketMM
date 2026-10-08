package com.example.projectmm.domain.dialogue;

import java.util.List;

public record WorldRoomTurn(
        String reply,
        WorldRoomCastChange castChange,
        List<WorldRoomSystemChange> systemChanges,
        long elapsedSeconds,
        String locationChange,
        List<String> addedConditions,
        List<String> removedConditions,
        List<String> addedInventory,
        List<String> removedInventory
) {
    public WorldRoomTurn {
        castChange = castChange == null ? WorldRoomCastChange.none() : castChange;
        systemChanges = systemChanges == null ? List.of() : List.copyOf(systemChanges);
        elapsedSeconds = elapsedSeconds < 0 ? 0 : elapsedSeconds;
        locationChange = locationChange == null || locationChange.isBlank() ? null : locationChange.trim();
        addedConditions = addedConditions == null ? List.of() : List.copyOf(addedConditions);
        removedConditions = removedConditions == null ? List.of() : List.copyOf(removedConditions);
        addedInventory = addedInventory == null ? List.of() : List.copyOf(addedInventory);
        removedInventory = removedInventory == null ? List.of() : List.copyOf(removedInventory);
    }

    public WorldRoomTurn(String reply, WorldRoomCastChange castChange, List<WorldRoomSystemChange> systemChanges, long elapsedSeconds, String locationChange) {
        this(reply, castChange, systemChanges, elapsedSeconds, locationChange, List.of(), List.of(), List.of(), List.of());
    }

    public WorldRoomTurn(String reply, WorldRoomCastChange castChange, List<WorldRoomSystemChange> systemChanges, long elapsedSeconds) {
        this(reply, castChange, systemChanges, elapsedSeconds, null);
    }

    public WorldRoomTurn(
            String reply,
            WorldRoomCastChange castChange,
            List<WorldRoomSystemChange> systemChanges
    ) {
        this(reply, castChange, systemChanges, 0, null);
    }

    public WorldRoomTurn(String reply, WorldRoomCastChange castChange) {
        this(reply, castChange, List.of(), 0, null);
    }
}
