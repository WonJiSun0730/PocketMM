package com.example.projectmm.domain.dialogue;

import java.time.LocalDateTime;
import java.util.List;

public record WorldRoomInitialScene(LocalDateTime simulatedAt, String location,
                                   List<String> inventory, List<String> conditions, String mode) {
    public WorldRoomInitialScene(LocalDateTime simulatedAt, String location, List<String> inventory, List<String> conditions) {
        this(simulatedAt, location, inventory, conditions, "RESET");
    }
}
