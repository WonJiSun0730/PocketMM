package com.example.projectmm.domain.dialogue;

public record WorldRoomTurn(
        String reply,
        WorldRoomCastChange castChange
) {
    public WorldRoomTurn {
        castChange = castChange == null ? WorldRoomCastChange.none() : castChange;
    }
}
