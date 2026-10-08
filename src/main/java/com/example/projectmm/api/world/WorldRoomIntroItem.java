package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomIntro;

import java.time.LocalDateTime;
import java.util.List;

public record WorldRoomIntroItem(
        Long id,
        String title,
        String content,
        int sortOrder,
        LocalDateTime initialSimulatedAt,
        String initialLocation,
        List<String> initialConditions,
        List<String> initialInventory
) {
    public static WorldRoomIntroItem from(WorldRoomIntro intro) {
        return new WorldRoomIntroItem(
                intro.getId(),
                intro.getTitle(),
                intro.getContent(),
                intro.getSortOrder(),
                intro.getInitialSimulatedAt(),
                intro.getInitialLocation(),
                List.copyOf(intro.getInitialConditions()),
                List.copyOf(intro.getInitialInventory())
        );
    }
}
