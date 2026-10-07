package com.example.projectmm.application.player;

import com.example.projectmm.domain.player.PlayerProfile;

public record PlayerProfileResponse(Long id, String name, String description, String avatar,
                                    String modelUrl, Double modelHeight) {
    public static PlayerProfileResponse from(PlayerProfile profile) {
        return new PlayerProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getDescription(),
                profile.getAvatar(),
                profile.getModelUrl(),
                profile.getModelHeight()
        );
    }
}
