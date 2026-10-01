package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomSession;

import java.time.LocalDateTime;

public record WorldRoomSessionResponse(
        Long id,
        Long roomId,
        Long playerProfileId,
        Long introId,
        String introTitle,
        String introContent,
        LocalDateTime simulatedAt
) {
    public static WorldRoomSessionResponse from(WorldRoomSession session) {
        return new WorldRoomSessionResponse(
                session.getId(),
                session.getRoom().getId(),
                session.getPlayerProfile() == null ? null : session.getPlayerProfile().getId(),
                session.getIntro().getId(),
                session.getIntro().getTitle(),
                session.getIntro().getContent(),
                session.getSimulatedAt()
        );
    }
}
