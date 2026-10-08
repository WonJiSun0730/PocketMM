package com.example.projectmm.api.world;

import com.example.projectmm.application.world.WorldService;
import com.example.projectmm.application.world.WorldSystemService;
import com.example.projectmm.application.world.WorldCharacterService;
import com.example.projectmm.application.chat.ChatHistoryResponse;
import com.example.projectmm.application.chat.ChatRequest;
import com.example.projectmm.application.chat.ChatResponse;
import com.example.projectmm.application.chat.ChatService;
import com.example.projectmm.application.chat.ConversationStartService;
import com.example.projectmm.application.chat.NewConversationResponse;
import com.example.projectmm.application.chat.WorldRoomService;
import com.example.projectmm.application.chat.WorldRoomDialogueService;
import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import com.example.projectmm.application.player.ConversationPlayerService;
import com.example.projectmm.application.player.PlayerSelectionRequest;
import com.example.projectmm.application.memory.PocketMemoryInspectorService;
import com.example.projectmm.domain.world.WorldLoreCategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/worlds")
@RequiredArgsConstructor
public class WorldController {

    private final WorldService worldService;
    private final WorldSystemService worldSystemService;
    private final WorldCharacterService worldCharacterService;
    private final ChatService chatService;
    private final ConversationStartService conversationStartService;
    private final ConversationPlayerService conversationPlayerService;
    private final WorldRoomService worldRoomService;
    private final WorldRoomDialogueService worldRoomDialogueService;
    private final WorldRoomSessionService worldRoomSessionService;
    private final PocketMemoryInspectorService pocketMemoryInspectorService;

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
    public WorldSystemsResponse replaceSystems(@PathVariable Long worldId, @jakarta.validation.Valid @RequestBody WorldSystemsRequest request, Authentication authentication) {
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
    @GetMapping("/{worldId}/rooms")
    public List<WorldRoomResponse> getRooms(
            @PathVariable Long worldId,
            Authentication authentication
    ) {
        return worldRoomService.ownedRooms(worldId, authentication.getName()).stream()
                .map(this::toRoomResponse)
                .toList();
    }

    @PostMapping("/{worldId}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldRoomResponse createRoom(
            @PathVariable Long worldId,
            @Valid @RequestBody WorldRoomCreateRequest request,
            Authentication authentication
    ) {
        var room = worldRoomService.create(
                worldId,
                authentication.getName(),
                request.title(),
                request.visibility()
        );
        return toRoomResponse(room);
    }

    @DeleteMapping("/{worldId}/rooms/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        var room = worldRoomService.get(roomId);
        ensureRoomWorld(worldId, room.getWorld().getId());
        worldRoomService.deleteRoom(roomId, authentication.getName());
    }

    @PutMapping("/{worldId}/rooms/{roomId}")
    public WorldRoomResponse updateRoom(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @Valid @RequestBody WorldRoomCreateRequest request,
            Authentication authentication
    ) {
        var room = worldRoomService.get(roomId);
        ensureRoomWorld(worldId, room.getWorld().getId());
        return toRoomResponse(worldRoomService.updateRoom(
                roomId,
                authentication.getName(),
                request.title(),
                request.visibility()
        ));
    }

    @PostMapping("/{worldId}/rooms/{roomId}/cast")
    public WorldRoomResponse addRoomCast(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @Valid @RequestBody WorldRoomCastRequest request,
            Authentication authentication
    ) {
        var cast = worldRoomService.addCast(roomId, request.characterId(), authentication.getName());
        ensureRoomWorld(worldId, cast.getConversation().getWorld().getId());
        return toRoomResponse(cast.getConversation());
    }

    @PostMapping("/{worldId}/rooms/{roomId}/intros")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldRoomIntroItem addRoomIntro(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @Valid @RequestBody WorldRoomIntroRequest request,
            Authentication authentication
    ) {
        var room = worldRoomService.get(roomId);
        ensureRoomWorld(worldId, room.getWorld().getId());
        return WorldRoomIntroItem.from(worldRoomService.addIntro(
                roomId,
                authentication.getName(),
                request.title(),
                request.content(),
                request.sortOrder(),
                request.initialSimulatedAt(),
                request.initialLocation(),
                request.initialConditions(),
                request.initialInventory()
        ));
    }

    @PutMapping("/{worldId}/rooms/{roomId}/intros/{introId}")
    public WorldRoomIntroItem updateRoomIntro(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long introId,
            @Valid @RequestBody WorldRoomIntroRequest request,
            Authentication authentication
    ) {
        var room = worldRoomService.get(roomId);
        ensureRoomWorld(worldId, room.getWorld().getId());
        return WorldRoomIntroItem.from(worldRoomService.updateIntro(
                roomId,
                introId,
                authentication.getName(),
                request.title(),
                request.content(),
                request.sortOrder(),
                request.initialSimulatedAt(),
                request.initialLocation(),
                request.initialConditions(),
                request.initialInventory()
        ));
    }

    @DeleteMapping("/{worldId}/rooms/{roomId}/intros/{introId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoomIntro(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long introId,
            Authentication authentication
    ) {
        var room = worldRoomService.get(roomId);
        ensureRoomWorld(worldId, room.getWorld().getId());
        worldRoomService.deleteIntro(roomId, introId, authentication.getName());
    }

    @GetMapping("/{worldId}/rooms/{roomId}/intros")
    public List<WorldRoomIntroItem> getRoomIntros(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        var room = worldRoomService.accessibleRoom(roomId, authentication.getName());
        ensureRoomWorld(worldId, room.getWorld().getId());
        return worldRoomService.roomIntros(roomId).stream()
                .map(WorldRoomIntroItem::from)
                .toList();
    }

    @GetMapping("/{worldId}/rooms/{roomId}")
    public WorldRoomResponse getRoom(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        var room = worldRoomService.accessibleRoom(roomId, authentication.getName());
        var cast = worldRoomService.roomCast(roomId);
        if (!cast.isEmpty()) {
            ensureRoomWorld(worldId, cast.get(0).getConversation().getWorld().getId());
        }
        ensureRoomWorld(worldId, room.getWorld().getId());
        return toRoomResponse(room);
    }

    private WorldRoomResponse toRoomResponse(com.example.projectmm.domain.chat.WorldConversation room) {
        var cast = worldRoomService.roomCast(room.getId());
        return new WorldRoomResponse(
                room.getId(),
                room.getWorld().getId(),
                room.getTitle(),
                room.getVisibility(),
                cast.stream().map(item -> new WorldRoomCastItem(
                        item.getCharacter().getId(),
                        item.getCharacter().getName()
                )).toList()
        );
    }

    private void ensureRoomWorld(Long requestedWorldId, Long actualWorldId) {
        if (!requestedWorldId.equals(actualWorldId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "World room not found"
            );
        }
    }

    @GetMapping("/pockets")
    public List<PocketListItem> pockets(Authentication authentication) {
        return worldRoomSessionService.pockets(authentication.getName()).stream()
                .map(PocketListItem::from)
                .toList();
    }

    @GetMapping("/pockets/{sessionId}")
    public WorldRoomSessionResponse pocket(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        return WorldRoomSessionResponse.from(
                worldRoomSessionService.pocket(sessionId, authentication.getName())
        );
    }

    @PutMapping("/pockets/{sessionId}/player-profile")
    public WorldRoomSessionResponse selectPocketPlayerProfile(
            @PathVariable Long sessionId,
            @RequestBody PlayerSelectionRequest request,
            Authentication authentication
    ) {
        return WorldRoomSessionResponse.from(
                worldRoomSessionService.selectPlayerProfile(
                        sessionId,
                        authentication.getName(),
                        request.playerProfileId()
                )
        );
    }

    @DeleteMapping("/pockets/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePocket(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        worldRoomSessionService.deletePocket(sessionId, authentication.getName());
    }

    @PostMapping("/{worldId}/rooms/{roomId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorldRoomSessionResponse startRoomSession(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @RequestBody WorldRoomSessionStartRequest request,
            Authentication authentication
    ) {
        var room = worldRoomService.get(roomId);
        ensureRoomWorld(worldId, room.getWorld().getId());
        return WorldRoomSessionResponse.from(
                worldRoomSessionService.start(
                        roomId,
                        authentication.getName(),
                        request.playerProfileId(),
                        request.introId()
                )
        );
    }

    @GetMapping("/{worldId}/rooms/{roomId}/session")
    public WorldRoomSessionResponse currentRoomSession(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        var room = worldRoomService.get(roomId);
        ensureRoomWorld(worldId, room.getWorld().getId());
        return WorldRoomSessionResponse.from(
                worldRoomSessionService.latest(roomId, authentication.getName())
        );
    }

    @PostMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/chat")
    public WorldRoomTurn chatInRoom(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @Valid @RequestBody WorldRoomChatRequest request,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomDialogueService.chat(sessionId, request.message());
    }

    @PostMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/continue")
    public WorldRoomTurn continueRoomScene(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomDialogueService.continueScene(sessionId);
    }

    @GetMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/messages")
    public List<WorldRoomMessageItem> getRoomMessages(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @RequestParam(required = false) Long before,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomDialogueService.history(sessionId, before).stream()
                .map(WorldRoomMessageItem::from)
                .toList();
    }

    @PutMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/messages/{messageId}")
    public WorldRoomMessageItem editRoomMessage(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @PathVariable Long messageId,
            @Valid @RequestBody WorldRoomMessageEditRequest request,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return WorldRoomMessageItem.from(
                worldRoomDialogueService.editMessage(sessionId, messageId, request.content())
        );
    }

    @GetMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/messages/{messageId}/variants")
    public List<WorldRoomMessageVariantItem> getRoomMessageVariants(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomDialogueService.variants(sessionId, messageId).stream()
                .map(variant -> WorldRoomMessageVariantItem.from(
                        variant,
                        variant.getMessage().getSelectedVariantNo()
                ))
                .toList();
    }

    @PutMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/messages/{messageId}/variants/{variantNo}")
    public WorldRoomMessageItem selectRoomMessageVariant(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @PathVariable Long messageId,
            @PathVariable int variantNo,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return WorldRoomMessageItem.from(
                worldRoomDialogueService.selectVariant(sessionId, messageId, variantNo)
        );
    }

    @PostMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/messages/{messageId}/reroll")
    public WorldRoomTurn rerollRoomMessage(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomDialogueService.reroll(sessionId, messageId);
    }

    @PostMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/messages/{messageId}/regenerate")
    public WorldRoomTurn regenerateRoomMessage(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomDialogueService.regenerate(sessionId, messageId);
    }

    @GetMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/characters")
    public List<WorldCharacterItem> getRoomSessionCharacters(
            @PathVariable Long worldId, @PathVariable Long roomId, @PathVariable Long sessionId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomSessionService.registeredCharacters(sessionId).stream()
                .map(character -> new WorldCharacterItem(character.getId(), character.getName(),
                        null, character.getModelUrl(), character.getModelHeight()))
                .toList();
    }

    @GetMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/cast")
    public List<WorldRoomSessionCastItem> getRoomSessionActiveCast(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomSessionService.activeCast(sessionId).stream()
                .map(WorldRoomSessionCastItem::from)
                .toList();
    }

    @GetMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/memory-inspector")
    public List<PocketMemoryInspectorService.CharacterMemories> inspectRoomSessionMemories(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return pocketMemoryInspectorService.latest(sessionId);
    }

    @GetMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/memory-inspector/diff")
    public PocketMemoryInspectorService.TurnDiff inspectRoomSessionMemoryDiff(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return pocketMemoryInspectorService.latestTurnDiff(sessionId);
    }

    @GetMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/systems")
    public List<WorldRoomSessionSystemItem> getRoomSessionSystems(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return worldRoomSessionService.systemStates(sessionId).stream()
                .map(WorldRoomSessionSystemItem::from)
                .toList();
    }

    @PutMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/systems/{systemId}")
    public WorldRoomSessionSystemItem setRoomSessionSystemValue(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @PathVariable Long systemId,
            @Valid @RequestBody WorldRoomSessionSystemValueRequest request,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        return WorldRoomSessionSystemItem.from(
                worldRoomSessionService.setSystemValue(sessionId, systemId, request.value())
        );
    }

    @DeleteMapping("/{worldId}/rooms/{roomId}/sessions/{sessionId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoomMessage(
            @PathVariable Long worldId,
            @PathVariable Long roomId,
            @PathVariable Long sessionId,
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        var session = worldRoomSessionService.ownedSession(roomId, sessionId, authentication.getName());
        if (!session.getId().equals(sessionId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Room play session not found"
            );
        }
        ensureRoomWorld(worldId, session.getRoom().getWorld().getId());
        worldRoomDialogueService.deleteMessageAndFollowing(sessionId, messageId);
    }

    @PostMapping("/{worldId}/characters/{characterId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public NewConversationResponse startWorldConversation(
            @PathVariable Long worldId,
            @PathVariable Long characterId,
            @Valid @RequestBody PlayerSelectionRequest request
    ) {
        return conversationStartService.startWorld(worldId, characterId, request.playerProfileId());
    }

    @GetMapping("/{worldId}/characters/{characterId}/session")
    public ConversationPlayerService.Session worldSession(@PathVariable Long worldId, @PathVariable Long characterId) {
        var room = chatService.getOrCreateWorldConversation(worldId, characterId);
        return conversationPlayerService.current(characterId, room.getId());
    }

    @PutMapping("/{worldId}/characters/{characterId}/player-profile")
    public ConversationPlayerService.Session selectWorldPlayer(
            @PathVariable Long worldId,
            @PathVariable Long characterId,
            @Valid @RequestBody PlayerSelectionRequest request
    ) {
        var room = chatService.getOrCreateWorldConversation(worldId, characterId);
        return conversationPlayerService.select(characterId, room.getId(), request.playerProfileId());
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

