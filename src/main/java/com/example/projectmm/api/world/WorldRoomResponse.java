package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldConversation;

import java.util.List;

public record WorldRoomResponse(
        Long id,
        Long worldId,
        String title,
        WorldConversation.Visibility visibility,
        List<WorldRoomCastItem> cast
) {}
