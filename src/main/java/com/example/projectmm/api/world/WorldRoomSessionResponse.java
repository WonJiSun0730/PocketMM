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
        LocalDateTime simulatedAt,
        InitialScene initialScene
) {
    public record InitialScene(LocalDateTime simulatedAt, String location,
                               java.util.List<String> inventory, java.util.List<String> conditions,
                               boolean reference) {}

    public static WorldRoomSessionResponse from(WorldRoomSession session) {
        boolean captured = session.getInitialSimulatedAt() != null;
        return new WorldRoomSessionResponse(
                session.getId(),
                session.getRoom().getId(),
                session.getPlayerProfile() == null ? null : session.getPlayerProfile().getId(),
                session.getIntro().getId(),
                captured ? session.getIntroTitleSnapshot() : session.getIntro().getTitle(),
                captured ? session.getIntroContentSnapshot() : session.getIntro().getContent(),
                session.getSimulatedAt(),
                new InitialScene(captured ? session.getInitialSimulatedAt()
                        : session.getIntro().getInitialSimulatedAt() == null ? session.getCreatedAt() : session.getIntro().getInitialSimulatedAt(),
                        captured ? session.getInitialLocation() : session.getIntro().getInitialLocation(),
                        java.util.List.copyOf(captured ? session.getInitialInventory() : session.getIntro().getInitialInventory()),
                        java.util.List.copyOf(captured ? session.getInitialConditions() : session.getIntro().getInitialConditions()),
                        !captured)
        );
    }
}
