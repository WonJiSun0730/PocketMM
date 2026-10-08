package com.example.projectmm.domain.dialogue;

import java.util.List;

public record WorldRoomTurn(
        String reply,
        WorldRoomCastChange castChange,
        List<WorldRoomSystemChange> systemChanges,
        long elapsedSeconds,
        String locationChange
) {
    public WorldRoomTurn {
        castChange = castChange == null ? WorldRoomCastChange.none() : castChange;
        systemChanges = systemChanges == null ? List.of() : List.copyOf(systemChanges);
        elapsedSeconds = elapsedSeconds < 0 ? 0 : elapsedSeconds;
        locationChange = locationChange == null || locationChange.isBlank() ? null : locationChange.trim();
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
