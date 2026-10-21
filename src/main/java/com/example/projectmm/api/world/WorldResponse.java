package com.example.projectmm.api.world;

public record WorldResponse(
        Long id,
        String name,
        String description,
        String coverImageUrl
) {
}
