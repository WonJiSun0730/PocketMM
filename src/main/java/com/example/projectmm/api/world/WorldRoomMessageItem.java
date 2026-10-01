package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldRoomMessage;

import java.time.LocalDateTime;

public record WorldRoomMessageItem(
        Long id,
        ChatRole role,
        String content,
        LocalDateTime createdAt,
        LocalDateTime editedAt,
        Integer selectedVariantNo
) {
    public static WorldRoomMessageItem from(WorldRoomMessage message) {
        return new WorldRoomMessageItem(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                message.getEditedAt(),
                message.getSelectedVariantNo()
        );
    }
}
