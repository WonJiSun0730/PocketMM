package com.example.projectmm.presentation.player;

import com.example.projectmm.application.player.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/player-profiles")
@RequiredArgsConstructor
public class PlayerProfileController {
    private final PlayerProfileService service;
    @GetMapping public List<PlayerProfileResponse> list() { return service.list(); }
    @PostMapping public PlayerProfileResponse create(@Valid @RequestBody PlayerProfileRequest request) {
        return service.save(null, request);
    }
    @PutMapping("/{id}") public PlayerProfileResponse update(@PathVariable Long id, @Valid @RequestBody PlayerProfileRequest request) {
        return service.save(id, request);
    }
}
