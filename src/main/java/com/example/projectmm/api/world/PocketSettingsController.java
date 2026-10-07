package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/worlds/pockets/{sessionId}/settings")
public class PocketSettingsController {

    private final WorldRoomSessionService sessions;

    @GetMapping
    public PocketSettingsResponse current(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        return PocketSettingsResponse.from(
                sessions.pocket(sessionId, authentication.getName())
        );
    }

    @PutMapping
    public PocketSettingsResponse update(
            @PathVariable Long sessionId,
            @RequestBody PocketSettingsRequest request,
            Authentication authentication
    ) {
        return PocketSettingsResponse.from(
                sessions.updatePocketSettings(
                        sessionId,
                        authentication.getName(),
                        request.userNote(),
                        request.playGuide(),
                        request.noControlPlayer()
                )
        );
    }
}
