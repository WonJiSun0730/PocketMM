package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.WorldConversation;
import com.example.projectmm.domain.chat.WorldConversationCast;
import com.example.projectmm.domain.chat.WorldConversationCastRepository;
import com.example.projectmm.domain.chat.WorldConversationRepository;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.player.PlayerProfileRepository;
import com.example.projectmm.domain.world.WorldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldRoomService {

    private final WorldRepository worlds;
    private final WorldConversationRepository rooms;
    private final WorldConversationCastRepository casts;
    private final CharacterRepository characters;
    private final PlayerProfileRepository players;
    private final MemberRepository members;

    @Transactional
    public WorldConversation create(
            Long worldId,
            String ownerLoginId,
            String title,
            WorldConversation.Visibility visibility,
            Long playerProfileId
    ) {
        var owner = members.findByLoginId(ownerLoginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return create(worldId, owner, title, visibility, playerProfileId);
    }

    @Transactional
    public WorldConversation create(
            Long worldId,
            Member owner,
            String title,
            WorldConversation.Visibility visibility,
            Long playerProfileId
    ) {
        var world = worlds.findById(worldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World not found"));
        if (owner != null && !world.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "World owner mismatch");
        }

        var now = now();
        var room = new WorldConversation(world, owner, title, visibility, now);
        if (playerProfileId != null) {
            room.selectPlayerProfile(players.findById(playerProfileId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player profile not found")));
        }
        return rooms.save(room);
    }

    @Transactional
    public WorldConversationCast addCast(Long roomId, Long characterId) {
        var room = room(roomId);
        var character = characters.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        if (character.getWorld() == null || !character.getWorld().getId().equals(room.getWorld().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Character is not assigned to this room's world");
        }
        return casts.findByConversationIdAndCharacterId(roomId, characterId)
                .orElseGet(() -> casts.save(new WorldConversationCast(room, character, now())));
    }

    @Transactional
    public WorldConversationCast enter(Long roomId, Long characterId) {
        var cast = cast(roomId, characterId);
        cast.enter(now());
        return cast;
    }

    @Transactional
    public WorldConversationCast leave(Long roomId, Long characterId) {
        var cast = cast(roomId, characterId);
        cast.leave(now());
        return cast;
    }

    @Transactional(readOnly = true)
    public List<WorldConversationCast> roomCast(Long roomId) {
        room(roomId);
        return casts.findByConversationIdOrderByJoinedAtAscIdAsc(roomId);
    }

    @Transactional(readOnly = true)
    public List<WorldConversationCast> activeCast(Long roomId) {
        room(roomId);
        return casts.findByConversationIdAndActiveTrueOrderByEnteredAtAscIdAsc(roomId);
    }

    private WorldConversationCast cast(Long roomId, Long characterId) {
        room(roomId);
        return casts.findByConversationIdAndCharacterId(roomId, characterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Character is not part of this room"
                ));
    }

    private WorldConversation room(Long roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World room not found"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
