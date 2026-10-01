package com.example.projectmm.domain.dialogue;

import java.util.List;

public record WorldRoomCastChange(
        List<Long> enterCharacterIds,
        List<Long> leaveCharacterIds
) {
    public WorldRoomCastChange {
        enterCharacterIds = enterCharacterIds == null ? List.of() : List.copyOf(enterCharacterIds);
        leaveCharacterIds = leaveCharacterIds == null ? List.of() : List.copyOf(leaveCharacterIds);
    }

    public static WorldRoomCastChange none() {
        return new WorldRoomCastChange(List.of(), List.of());
    }
}
