package com.example.projectmm.application.player;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/characters/{characterId}/conversations/{conversationId}/player-state")
public class PlayerCoreStateController {

    private final ConversationPlayerService playerService;

    public record UpdateRequest(
            int energy,
            int mood
    ) {}

    @GetMapping
    public ConversationPlayerService.CoreState current(
            @PathVariable Long characterId,
            @PathVariable Long conversationId
    ) {
        return playerService.currentCoreState(
                characterId,
                conversationId
        );
    }

    @PatchMapping
    public ConversationPlayerService.CoreState update(
            @PathVariable Long characterId,
            @PathVariable Long conversationId,
            @RequestBody UpdateRequest request
    ) {
        return playerService.updateCoreState(
                characterId,
                conversationId,
                request.energy(),
                request.mood()
        );
    }
}