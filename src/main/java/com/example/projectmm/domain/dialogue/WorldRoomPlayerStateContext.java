package com.example.projectmm.domain.dialogue;

import java.util.List;

public record WorldRoomPlayerStateContext(
        String location,
        List<String> conditions,
        List<String> inventory
) {
    public WorldRoomPlayerStateContext {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        inventory = inventory == null ? List.of() : List.copyOf(inventory);
    }

    public static WorldRoomPlayerStateContext empty() {
        return new WorldRoomPlayerStateContext(null, List.of(), List.of());
    }
}
