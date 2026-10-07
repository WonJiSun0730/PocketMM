package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomSession;

public record PocketSettingsResponse(
        Long sessionId,
        String userNote,
        String playGuide,
        boolean noControlPlayer
) {
    public static PocketSettingsResponse from(WorldRoomSession session) {
        return new PocketSettingsResponse(
                session.getId(),
                session.getUserNote(),
                session.getPocketPlayGuide(),
                session.isNoControlPlayer()
        );
    }
}
