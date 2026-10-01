package com.example.projectmm.domain.dialogue;

import java.util.List;

public record WorldRoomTurn(
        String reply,
        WorldRoomCastChange castChange,
        List<WorldRoomSystemChange> systemChanges
) {
    public WorldRoomTurn {
        castChange = castChange == null ? WorldRoomCastChange.none() : castChange;
        systemChanges = systemChanges == null ? List.of() : List.copyOf(systemChanges);
    }

    public WorldRoomTurn(String reply, WorldRoomCastChange castChange) {
        this(reply, castChange, List.of());
    }
}
