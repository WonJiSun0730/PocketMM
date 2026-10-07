package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomSessionCast;

import java.time.LocalDateTime;

public record WorldRoomSessionCastItem(
        Long characterId,
        String characterName,
        String modelUrl,
        Double modelHeight,
        LocalDateTime enteredAt
) {
    public static WorldRoomSessionCastItem from(WorldRoomSessionCast state) {
        return new WorldRoomSessionCastItem(
                state.getCharacter().getId(),
                state.getCharacter().getName(),
                state.getCharacter().getModelUrl(),
                state.getCharacter().getModelHeight(),
                state.getEnteredAt()
        );
    }
}
