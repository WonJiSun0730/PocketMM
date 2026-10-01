package com.example.projectmm.api.world;

public record WorldRoomSessionStartRequest(
        Long playerProfileId,
        Long introId
) {}
