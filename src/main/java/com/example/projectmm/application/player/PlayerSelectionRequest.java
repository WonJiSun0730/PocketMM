package com.example.projectmm.application.player;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlayerSelectionRequest(@NotNull @Positive Long playerProfileId) {}
