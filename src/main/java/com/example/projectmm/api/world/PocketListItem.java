package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomSession;

import java.time.LocalDateTime;

public record PocketListItem(
        Long sessionId,
        Long roomId,
        String roomTitle,
        Long worldId,
        String worldName,
        Long introId,
        String introTitle,
        Long playerProfileId,
        String playerProfileName,
        LocalDateTime createdAt,
        LocalDateTime simulatedAt
) {
    public static PocketListItem from(WorldRoomSession session) {
        var player = session.getPlayerProfile();
        return new PocketListItem(
                session.getId(),
                session.getRoom().getId(),
                session.getRoom().getTitle(),
                session.getRoom().getWorld().getId(),
                session.getRoom().getWorld().getName(),
                session.getIntro().getId(),
                session.getIntro().getTitle(),
                player == null ? null : player.getId(),
                player == null ? null : player.getName(),
                session.getCreatedAt(),
                session.getSimulatedAt()
        );
    }
}
