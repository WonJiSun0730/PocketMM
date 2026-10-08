package com.example.projectmm.api.world;
import java.util.List;
public record WorldSystemsRequest(String playGuide, List<@jakarta.validation.Valid @jakarta.validation.constraints.NotNull WorldSystemItem> systems) {}
