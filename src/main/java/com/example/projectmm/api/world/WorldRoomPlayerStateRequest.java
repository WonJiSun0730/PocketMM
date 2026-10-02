package com.example.projectmm.api.world;

import java.time.LocalDateTime;
import java.util.List;

public record WorldRoomPlayerStateRequest(
        LocalDateTime simulatedAt,
        String location,
        List<String> conditions,
        List<String> inventory
) {
}
