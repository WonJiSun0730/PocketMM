package com.example.projectmm.application.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerProfileRequest(@NotBlank @Size(max = 40) String name,
                                   @NotBlank @Size(max = 500) String description,
                                   @Size(max = 400000) String avatar,
                                   @Size(max = 500) String modelUrl,
                                   Double modelHeight) {}
