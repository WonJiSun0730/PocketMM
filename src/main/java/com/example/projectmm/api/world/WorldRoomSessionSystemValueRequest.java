package com.example.projectmm.api.world;

import jakarta.validation.constraints.NotNull;

public record WorldRoomSessionSystemValueRequest(
        @NotNull Integer value
) {
}
