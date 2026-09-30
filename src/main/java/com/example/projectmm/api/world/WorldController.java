package com.example.projectmm.api.world;

import com.example.projectmm.application.world.WorldService;
import com.example.projectmm.domain.world.WorldLoreCategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/worlds")
@RequiredArgsConstructor
public class WorldController {

    private final WorldService worldService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorldResponse create(
            @Valid @RequestBody WorldCreateRequest request,
            Authentication authentication
    ) {
        return worldService.create(request, authentication.getName());
    }

    @GetMapping
    public List<WorldResponse> getAll(Authentication authentication) {
        return worldService.getAll(authentication.getName());
    }

    @GetMapping("/{worldId}")
    public WorldResponse getById(
            @PathVariable Long worldId,
            Authentication authentication
    ) {
        return worldService.getById(worldId, authentication.getName());
    }

    @GetMapping("/{worldId}/lore/{category}")
    public WorldLoreResponse getLore(
            @PathVariable Long worldId,
            @PathVariable WorldLoreCategory category,
            Authentication authentication
    ) {
        return worldService.getLore(worldId, category, authentication.getName());
    }

    @PutMapping("/{worldId}/lore/{category}")
    public WorldLoreResponse replaceLore(
            @PathVariable Long worldId,
            @PathVariable WorldLoreCategory category,
            @RequestBody WorldLoreUpdateRequest request,
            Authentication authentication
    ) {
        return worldService.replaceLore(worldId, category, request.entries(), authentication.getName());
    }
}
