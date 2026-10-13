package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.PocketActivityReportRepository;
import com.example.projectmm.domain.chat.WorldConversation;
import com.example.projectmm.domain.chat.WorldConversationCast;
import com.example.projectmm.domain.chat.WorldConversationCastRepository;
import com.example.projectmm.domain.chat.WorldConversationRepository;
import com.example.projectmm.domain.chat.WorldRoomIntro;
import com.example.projectmm.domain.chat.WorldRoomIntroRepository;
import com.example.projectmm.domain.chat.WorldRoomMessageRepository;
import com.example.projectmm.domain.chat.WorldRoomPlayerStateRepository;
import com.example.projectmm.domain.chat.WorldRoomMessageVariantRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionCastRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionSystemStateRepository;
import com.example.projectmm.domain.chat.WorldRoomVariantCastSnapshotRepository;
import com.example.projectmm.domain.chat.WorldRoomVariantSystemSnapshotRepository;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
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
    private final MemberRepository members;
    private final WorldRoomIntroRepository intros;
    private final WorldRoomSessionRepository sessions;
    private final PocketActivityReportRepository activityReports;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final WorldRoomVariantCastSnapshotRepository castSnapshots;
    private final WorldRoomVariantSystemSnapshotRepository systemSnapshots;
    private final WorldRoomSessionCastRepository sessionCasts;
    private final WorldRoomSessionSystemStateRepository sessionSystemStates;
    private final WorldRoomPlayerStateRepository playerStates;
    private final com.example.projectmm.domain.memory.MemoryRepository memories;
    private final com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository memoryDetails;
    private final jakarta.persistence.EntityManager em;

    @Transactional
    public WorldConversation create(
            Long worldId,
            String ownerLoginId,
            String title,
            WorldConversation.Visibility visibility
    ) {
        var owner = members.findByLoginId(ownerLoginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return create(worldId, owner, title, visibility);
    }

    @Transactional
    public WorldConversation create(
            Long worldId,
            Member owner,
            String title,
            WorldConversation.Visibility visibility
    ) {
        var world = worlds.findById(worldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World not found"));
        if (owner != null && !world.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "World owner mismatch");
        }

        var now = now();
        var room = new WorldConversation(world, owner, title, visibility, now);
        return rooms.save(room);
    }

    @Transactional
    public WorldConversation updateRoom(
            Long roomId,
            String ownerLoginId,
            String title,
            WorldConversation.Visibility visibility
    ) {
        var room = room(roomId);
        var owner = members.findByLoginId(ownerLoginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (room.getOwner() == null || !room.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room creator can change room settings");
        }
        room.updateRoom(title, visibility);
        return room;
    }

    @Transactional
    public void deleteRoom(Long roomId, String ownerLoginId) {
        var room = ownedRoom(roomId, ownerLoginId);

        for (var pocket : sessions.findByRoomIdOrderByCreatedAtDescIdDesc(roomId)) {
            var locked = sessions.lockById(pocket.getId()).orElseThrow(); em.refresh(locked);
            if (locked.progressInProgress(LocalDateTime.now()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "부재 활동을 정리 중이에요. 잠시 기다려 주세요.");
            var memoryIds = memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(pocket.getId())
                    .stream().map(com.example.projectmm.domain.memory.Memory::getId).toList();
            memories.deleteWithDependencies(memoryIds);
        }
        memories.flush();
        activityReports.deleteBySessionRoomId(roomId);
        systemSnapshots.deleteByVariantMessageSessionRoomId(roomId);
        castSnapshots.deleteByVariantMessageSessionRoomId(roomId);
        variants.deleteByMessageSessionRoomId(roomId);
        messages.deleteBySessionRoomId(roomId);
        sessionCasts.deleteBySessionRoomId(roomId);
        sessionSystemStates.deleteBySessionRoomId(roomId);
        sessions.findByRoomIdOrderByCreatedAtDescIdDesc(roomId)
                .forEach(session -> playerStates.deleteBySessionId(session.getId()));
        playerStates.flush();
        sessions.deleteByRoomId(roomId);
        intros.deleteByRoomId(roomId);
        casts.deleteByConversationId(roomId);
        rooms.delete(room);
    }

    @Transactional
    public WorldConversationCast addCast(Long roomId, Long characterId, String ownerLoginId) {
        var room = room(roomId);
        var owner = members.findByLoginId(ownerLoginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (room.getOwner() == null || !room.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room creator can change room cast");
        }
        var character = characters.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        if (character.getWorld() == null || !character.getWorld().getId().equals(room.getWorld().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Character is not assigned to this room's world");
        }
        return casts.findByConversationIdAndCharacterId(roomId, characterId)
                .orElseGet(() -> casts.save(new WorldConversationCast(room, character, now())));
    }

    @Transactional
    public WorldRoomIntro addIntro(
            Long roomId,
            String ownerLoginId,
            String title,
            String content,
            Integer sortOrder,
            LocalDateTime initialSimulatedAt,
            String initialLocation,
            List<String> initialConditions,
            List<String> initialInventory
    ) {
        var room = ownedRoom(roomId, ownerLoginId);
        int resolvedSortOrder = sortOrder == null
                ? intros.findByRoomIdOrderBySortOrderAscIdAsc(roomId).size()
                : sortOrder;
        return intros.save(new WorldRoomIntro(room, title, content, resolvedSortOrder, initialSimulatedAt, initialLocation, initialConditions, initialInventory));
    }

    @Transactional
    public WorldRoomIntro updateIntro(
            Long roomId,
            Long introId,
            String ownerLoginId,
            String title,
            String content,
            Integer sortOrder,
            LocalDateTime initialSimulatedAt,
            String initialLocation,
            List<String> initialConditions,
            List<String> initialInventory
    ) {
        ownedRoom(roomId, ownerLoginId);
        var intro = intros.findByIdAndRoomId(introId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room intro not found"));
        intro.update(title, content, sortOrder == null ? intro.getSortOrder() : sortOrder, initialSimulatedAt, initialLocation, initialConditions, initialInventory);
        return intro;
    }

    @Transactional
    public void deleteIntro(Long roomId, Long introId, String ownerLoginId) {
        ownedRoom(roomId, ownerLoginId);
        var intro = intros.findByIdAndRoomId(introId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room intro not found"));
        intros.delete(intro);
    }

    @Transactional(readOnly = true)
    public List<WorldRoomIntro> roomIntros(Long roomId) {
        room(roomId);
        return intros.findByRoomIdOrderBySortOrderAscIdAsc(roomId);
    }

    private WorldConversation ownedRoom(Long roomId, String ownerLoginId) {
        var room = room(roomId);
        var owner = members.findByLoginId(ownerLoginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (room.getOwner() == null || !room.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room creator can change room intros");
        }
        return room;
    }

    @Transactional(readOnly = true)
    public List<WorldConversation> ownedRooms(Long worldId, String ownerLoginId) {
        var world = worlds.findById(worldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World not found"));
        var owner = members.findByLoginId(ownerLoginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (!world.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "World not found");
        }
        return rooms.findByWorldIdOrderByCreatedAtDescIdDesc(worldId);
    }

    @Transactional(readOnly = true)
    public WorldConversation accessibleRoom(Long roomId, String loginId) {
        var room = room(roomId);
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (room.getVisibility() == WorldConversation.Visibility.PRIVATE
                && (room.getOwner() == null || !room.getOwner().getId().equals(member.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "World room not found");
        }
        return room;
    }

    @Transactional(readOnly = true)
    public WorldConversation get(Long roomId) {
        return room(roomId);
    }

    @Transactional(readOnly = true)
    public List<WorldConversationCast> roomCast(Long roomId) {
        room(roomId);
        return casts.findByConversationIdOrderByJoinedAtAscIdAsc(roomId);
    }

    private WorldConversation room(Long roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World room not found"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
