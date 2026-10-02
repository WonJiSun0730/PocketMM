package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomPlayerState;
import com.example.projectmm.domain.chat.WorldRoomSession;

import java.time.LocalDateTime;
import java.util.List;

public record WorldRoomPlayerStateResponse(
        Long sessionId,
        LocalDateTime simulatedAt,
        String location,
        List<String> conditions,
        List<String> inventory
) {
    public static WorldRoomPlayerStateResponse from(
            WorldRoomSession session,
            WorldRoomPlayerState state
    ) {
        return new WorldRoomPlayerStateResponse(
                session.getId(),
                session.getSimulatedAt(),
                state.getLocation(),
                List.copyOf(state.getConditions()),
                List.copyOf(state.getInventory())
        );
    }
}
