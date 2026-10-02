package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomPlayerStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worlds/pockets/{sessionId}/player-state")
public class WorldRoomPlayerStateController {

    private final WorldRoomPlayerStateService playerStateService;

    @GetMapping
    public WorldRoomPlayerStateResponse current(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        var state = playerStateService.current(sessionId, authentication.getName());
        return WorldRoomPlayerStateResponse.from(state.getSession(), state);
    }

    @PutMapping
    public WorldRoomPlayerStateResponse update(
            @PathVariable Long sessionId,
            @RequestBody WorldRoomPlayerStateRequest request,
            Authentication authentication
    ) {
        var state = playerStateService.update(
                sessionId,
                authentication.getName(),
                request.simulatedAt(),
                request.location(),
                request.conditions(),
                request.inventory()
        );
        return WorldRoomPlayerStateResponse.from(state.getSession(), state);
    }
}
