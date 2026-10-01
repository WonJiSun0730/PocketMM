package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomIntro;

public record WorldRoomIntroItem(
        Long id,
        String title,
        String content,
        int sortOrder
) {
    public static WorldRoomIntroItem from(WorldRoomIntro intro) {
        return new WorldRoomIntroItem(
                intro.getId(),
                intro.getTitle(),
                intro.getContent(),
                intro.getSortOrder()
        );
    }
}
