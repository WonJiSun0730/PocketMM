package com.example.projectmm.api.world;

public record PocketSettingsRequest(
        String userNote,
        String playGuide,
        boolean noControlPlayer
) {
}
