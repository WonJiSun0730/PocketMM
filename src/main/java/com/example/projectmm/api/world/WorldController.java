package com.example.projectmm.api.world;

import com.example.projectmm.application.world.WorldService;
import com.example.projectmm.application.world.WorldSystemService;
import com.example.projectmm.application.world.WorldCharacterService;
import com.example.projectmm.application.chat.ChatHistoryResponse;
import com.example.projectmm.application.chat.ChatRequest;
import com.example.projectmm.application.chat.ChatResponse;
import com.example.projectmm.application.chat.ChatService;
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
    private final WorldSystemService worldSystemService;
    private final WorldCharacterService worldCharacterService;
    private final ChatService chatService;

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

    @PutMapping("/{worldId}")
    public WorldResponse update(
            @PathVariable Long worldId,
            @Valid @RequestBody WorldUpdateRequest request,
            Authentication authentication
    ) {
        return worldService.update(worldId, request, authentication.getName());
    }

    @DeleteMapping("/{worldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long worldId, Authentication authentication) {
        worldService.delete(worldId, authentication.getName());
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
    @GetMapping("/{worldId}/systems")
    public WorldSystemsResponse getSystems(@PathVariable Long worldId, Authentication authentication) {
        return worldSystemService.get(worldId, authentication.getName());
    }

    @PutMapping("/{worldId}/systems")
    public WorldSystemsResponse replaceSystems(@PathVariable Long worldId, @RequestBody WorldSystemsRequest request, Authentication authentication) {
        return worldSystemService.replace(worldId, request, authentication.getName());
    }
    @GetMapping("/{worldId}/characters")
    public List<WorldCharacterItem> getCharacters(@PathVariable Long worldId, Authentication authentication) {
        return worldCharacterService.get(worldId, authentication.getName());
    }

    @PutMapping("/{worldId}/characters")
    public List<WorldCharacterItem> replaceCharacters(@PathVariable Long worldId, @RequestBody WorldCharacterUpdateRequest request, Authentication authentication) {
        return worldCharacterService.replace(worldId, request.characterIds(), authentication.getName());
    }
    @PostMapping("/{worldId}/characters/{characterId}/chat")
    public ChatResponse chatInWorld(@PathVariable Long worldId, @PathVariable Long characterId, @RequestBody ChatRequest request) {
        return chatService.chatInWorld(worldId, characterId, request);
    }

    @GetMapping("/{worldId}/characters/{characterId}/messages")
    public List<ChatHistoryResponse> getWorldMessages(@PathVariable Long worldId, @PathVariable Long characterId) {
        return chatService.getRecentWorldMessages(worldId, characterId);
    }
}



