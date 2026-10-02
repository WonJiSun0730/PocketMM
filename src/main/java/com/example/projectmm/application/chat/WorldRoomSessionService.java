package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomSystemChange;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository;
import com.example.projectmm.domain.player.PlayerProfileRepository;
import com.example.projectmm.domain.world.WorldSystemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldRoomSessionService {

    private final WorldConversationRepository rooms;
    private final WorldConversationCastRepository roomCasts;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomSessionCastRepository sessionCasts;
    private final WorldRoomVariantCastSnapshotRepository castSnapshots;
    private final WorldRoomSessionSystemStateRepository systemStates;
    private final WorldRoomVariantSystemSnapshotRepository systemSnapshots;
    private final WorldSystemRepository worldSystems;
    private final MemberRepository members;
    private final PlayerProfileRepository players;
    private final WorldRoomIntroRepository intros;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final MemoryRepository memories;
    private final ProtectedMemoryDetailRepository protectedMemoryDetails;

    @Transactional
    public WorldRoomSession start(Long roomId, String loginId, Long playerProfileId, Long introId) {
        var room = room(roomId);
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (room.getVisibility() == WorldConversation.Visibility.PRIVATE
                && (room.getOwner() == null || !room.getOwner().getId().equals(member.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "World room not found");
        }
        var player = playerProfileId == null ? null : players.findById(playerProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player profile not found"));
        if (introId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room intro is required");
        }
        var intro = intros.findByIdAndRoomId(introId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room intro not found"));
        var session = sessions.save(new WorldRoomSession(room, member, player, intro, now()));
        worldSystems.findByWorldIdOrderBySortOrderAsc(room.getWorld().getId())
                .forEach(system -> systemStates.save(new WorldRoomSessionSystemState(
                        session,
                        system,
                        system.getInitialValue()
                )));
        return session;
    }

    @Transactional(readOnly = true)
    public List<WorldRoomSession> pockets(String loginId) {
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return sessions.findByMemberIdOrderBySimulatedAtDescIdDesc(member.getId());
    }

    @Transactional(readOnly = true)
    public WorldRoomSession pocket(Long sessionId, String loginId) {
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pocket not found"));
        if (!session.getMember().getId().equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pocket not found");
        }
        return session;
    }

    @Transactional
    public WorldRoomSession selectPlayerProfile(Long sessionId, String loginId, Long playerProfileId) {
        var session = pocket(sessionId, loginId);
        var player = playerProfileId == null ? null : players.findById(playerProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player profile not found"));
        session.selectPlayerProfile(player);
        return session;
    }

    @Transactional
    public void deletePocket(Long sessionId, String loginId) {
        var session = pocket(sessionId, loginId);

        var memoryIds = memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(sessionId)
                .stream()
                .map(com.example.projectmm.domain.memory.Memory::getId)
                .toList();
        if (!memoryIds.isEmpty()) {
            protectedMemoryDetails.deleteByMemoryIdIn(memoryIds);
        }
        memories.deleteByWorldRoomSessionId(sessionId);

        systemSnapshots.deleteByVariantMessageSessionId(sessionId);
        castSnapshots.deleteByVariantMessageSessionId(sessionId);
        variants.deleteByMessageSessionId(sessionId);
        messages.deleteBySessionId(sessionId);
        sessionCasts.deleteBySessionId(sessionId);
        systemStates.deleteBySessionId(sessionId);
        sessions.delete(session);
    }

    @Transactional(readOnly = true)
    public WorldRoomSession latest(Long roomId, String loginId) {
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return sessions.findTopByRoomIdAndMemberIdOrderByCreatedAtDescIdDesc(roomId, member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room play session not found"));
    }

    @Transactional(readOnly = true)
    public WorldRoomSession ownedSession(Long roomId, Long sessionId, String loginId) {
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return sessions.findByIdAndRoomIdAndMemberId(sessionId, roomId, member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room play session not found"));
    }

    @Transactional
    public WorldRoomCastChange applyCastChange(Long sessionId, WorldRoomCastChange proposed) {
        var session = session(sessionId);
        if (proposed == null) {
            return WorldRoomCastChange.none();
        }

        var allowed = roomCasts.findByConversationIdOrderByJoinedAtAscIdAsc(session.getRoom().getId())
                .stream()
                .map(cast -> cast.getCharacter().getId())
                .collect(java.util.stream.Collectors.toSet());

        var entered = new ArrayList<Long>();
        var left = new ArrayList<Long>();
        var seen = new HashSet<Long>();

        for (Long characterId : proposed.enterCharacterIds()) {
            if (characterId == null || !allowed.contains(characterId) || !seen.add(characterId)) {
                continue;
            }
            var state = sessionCasts.findBySessionIdAndCharacterId(sessionId, characterId)
                    .orElseGet(() -> sessionCasts.save(new WorldRoomSessionCast(
                            session,
                            roomCasts.findByConversationIdAndCharacterId(session.getRoom().getId(), characterId)
                                    .orElseThrow()
                                    .getCharacter()
                    )));
            if (!state.isActive()) {
                state.enter(now());
                entered.add(characterId);
            }
        }

        seen.clear();
        for (Long characterId : proposed.leaveCharacterIds()) {
            if (characterId == null || !allowed.contains(characterId)
                    || !seen.add(characterId) || entered.contains(characterId)) {
                continue;
            }
            var state = sessionCasts.findBySessionIdAndCharacterId(sessionId, characterId).orElse(null);
            if (state != null && state.isActive()) {
                state.leave(now());
                left.add(characterId);
            }
        }

        return new WorldRoomCastChange(entered, left);
    }

    @Transactional(readOnly = true)
    public List<CastState> captureCastState(Long sessionId) {
        var session = session(sessionId);
        return roomCasts.findByConversationIdOrderByJoinedAtAscIdAsc(session.getRoom().getId())
                .stream()
                .map(cast -> {
                    var state = sessionCasts.findBySessionIdAndCharacterId(
                            sessionId,
                            cast.getCharacter().getId()
                    ).orElse(null);
                    return new CastState(
                            cast.getCharacter().getId(),
                            state != null && state.isActive(),
                            state == null ? null : state.getEnteredAt(),
                            state == null ? null : state.getLeftAt()
                    );
                })
                .toList();
    }

    @Transactional
    public void saveCastSnapshot(
            WorldRoomMessageVariant variant,
            List<CastState> before,
            List<CastState> after
    ) {
        var afterByCharacter = after.stream()
                .collect(java.util.stream.Collectors.toMap(CastState::characterId, state -> state));
        for (CastState previous : before) {
            var next = afterByCharacter.get(previous.characterId());
            if (next == null) {
                throw new IllegalStateException(
                        "Cast state missing after generation. characterId=" + previous.characterId()
                );
            }
            castSnapshots.save(new WorldRoomVariantCastSnapshot(
                    variant,
                    previous.characterId(),
                    previous.active(),
                    previous.enteredAt(),
                    previous.leftAt(),
                    next.active(),
                    next.enteredAt(),
                    next.leftAt()
            ));
        }
    }

    @Transactional
    public void restoreCastBeforeVariant(Long sessionId, WorldRoomMessageVariant variant) {
        var session = session(sessionId);
        var snapshots = castSnapshots.findByVariantIdOrderByCharacterIdAsc(variant.getId());
        if (snapshots.isEmpty()) {
            var roomCast = roomCasts.findByConversationIdOrderByJoinedAtAscIdAsc(session.getRoom().getId());
            if (roomCast.isEmpty()) {
                return;
            }
            throw new IllegalStateException("Cast snapshot not found. variantId=" + variant.getId());
        }
        for (var snapshot : snapshots) {
            var roomCast = roomCasts.findByConversationIdAndCharacterId(
                    session.getRoom().getId(), snapshot.getCharacterId()
            ).orElseThrow();
            var state = sessionCasts.findBySessionIdAndCharacterId(
                    sessionId, snapshot.getCharacterId()
            ).orElseGet(() -> sessionCasts.save(
                    new WorldRoomSessionCast(session, roomCast.getCharacter())
            ));
            state.restore(
                    snapshot.isBeforeActive(),
                    snapshot.getBeforeEnteredAt(),
                    snapshot.getBeforeLeftAt()
            );
        }
    }

    @Transactional
    public void restoreCastAfterVariant(Long sessionId, WorldRoomMessageVariant variant) {
        var session = session(sessionId);
        var snapshots = castSnapshots.findByVariantIdOrderByCharacterIdAsc(variant.getId());
        if (snapshots.isEmpty()) {
            var roomCast = roomCasts.findByConversationIdOrderByJoinedAtAscIdAsc(session.getRoom().getId());
            if (roomCast.isEmpty()) {
                return;
            }
            throw new IllegalStateException(
                    "Cast snapshot not found. variantId=" + variant.getId()
            );
        }

        for (var snapshot : snapshots) {
            var roomCast = roomCasts.findByConversationIdAndCharacterId(
                            session.getRoom().getId(),
                            snapshot.getCharacterId()
                    )
                    .orElseThrow(() -> new IllegalStateException(
                            "Room cast not found. characterId=" + snapshot.getCharacterId()
                    ));
            var state = sessionCasts.findBySessionIdAndCharacterId(
                            sessionId,
                            snapshot.getCharacterId()
                    )
                    .orElseGet(() -> sessionCasts.save(new WorldRoomSessionCast(
                            session,
                            roomCast.getCharacter()
                    )));
            state.restore(
                    snapshot.isAfterActive(),
                    snapshot.getAfterEnteredAt(),
                    snapshot.getAfterLeftAt()
            );
        }
    }

    public record CastState(
            Long characterId,
            boolean active,
            LocalDateTime enteredAt,
            LocalDateTime leftAt
    ) {
    }

    @Transactional(readOnly = true)
    public List<SystemState> captureSystemState(Long sessionId) {
        session(sessionId);
        return systemStates.findBySessionIdOrderBySystemSortOrderAscIdAsc(sessionId)
                .stream()
                .map(state -> new SystemState(state.getSystem().getId(), state.getCurrentValue()))
                .toList();
    }

    @Transactional
    public void saveSystemSnapshot(
            WorldRoomMessageVariant variant,
            List<SystemState> before,
            List<SystemState> after
    ) {
        var afterBySystem = after.stream()
                .collect(java.util.stream.Collectors.toMap(SystemState::systemId, state -> state));
        for (SystemState previous : before) {
            var next = afterBySystem.get(previous.systemId());
            if (next == null) {
                throw new IllegalStateException("System state missing after generation. systemId=" + previous.systemId());
            }
            systemSnapshots.save(new WorldRoomVariantSystemSnapshot(
                    variant, previous.systemId(), previous.currentValue(), next.currentValue()
            ));
        }
    }

    @Transactional
    public void restoreSystemsBeforeVariant(Long sessionId, WorldRoomMessageVariant variant) {
        restoreSystemSnapshot(sessionId, variant, true);
    }

    @Transactional
    public void restoreSystemsAfterVariant(Long sessionId, WorldRoomMessageVariant variant) {
        restoreSystemSnapshot(sessionId, variant, false);
    }

    private void restoreSystemSnapshot(Long sessionId, WorldRoomMessageVariant variant, boolean before) {
        session(sessionId);
        var snapshots = systemSnapshots.findByVariantIdOrderBySystemIdAsc(variant.getId());
        if (snapshots.isEmpty() && !systemStates.findBySessionIdOrderBySystemSortOrderAscIdAsc(sessionId).isEmpty()) {
            throw new IllegalStateException("System snapshot not found. variantId=" + variant.getId());
        }
        for (var snapshot : snapshots) {
            var state = systemStates.findBySessionIdAndSystemId(sessionId, snapshot.getSystemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Room session system state not found. systemId=" + snapshot.getSystemId()
                    ));
            state.setCurrentValue(before ? snapshot.getBeforeValue() : snapshot.getAfterValue());
        }
    }

    public record SystemState(Long systemId, int currentValue) {
    }

    @Transactional
    public List<WorldRoomSystemChange> applySystemChanges(
            Long sessionId,
            List<WorldRoomSystemChange> proposed
    ) {
        session(sessionId);
        if (proposed == null || proposed.isEmpty()) {
            return List.of();
        }

        var applied = new ArrayList<WorldRoomSystemChange>();
        var seen = new HashSet<Long>();
        for (WorldRoomSystemChange change : proposed) {
            if (change == null
                    || change.systemId() == null
                    || change.delta() == 0
                    || !seen.add(change.systemId())) {
                continue;
            }
            var state = systemStates.findBySessionIdAndSystemId(sessionId, change.systemId())
                    .orElse(null);
            if (state == null) {
                continue;
            }
            int before = state.getCurrentValue();
            state.applyDelta(change.delta());
            int actualDelta = state.getCurrentValue() - before;
            if (actualDelta != 0) {
                applied.add(new WorldRoomSystemChange(change.systemId(), actualDelta));
            }
        }
        return List.copyOf(applied);
    }

    @Transactional(readOnly = true)
    public List<WorldRoomSessionSystemState> systemStates(Long sessionId) {
        session(sessionId);
        return systemStates.findBySessionIdOrderBySystemSortOrderAscIdAsc(sessionId);
    }

    @Transactional
    public WorldRoomSessionSystemState setSystemValue(
            Long sessionId,
            Long systemId,
            int value
    ) {
        var session = session(sessionId);
        var state = systemStates.findBySessionIdAndSystemId(sessionId, systemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Room session system state not found"
                ));
        if (!state.getSystem().getWorld().getId().equals(session.getRoom().getWorld().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "World system does not belong to session world");
        }
        state.setCurrentValue(value);
        return state;
    }

    @Transactional(readOnly = true)
    public List<WorldRoomSessionCast> activeCast(Long sessionId) {
        session(sessionId);
        return sessionCasts.findBySessionIdAndActiveTrueOrderByEnteredAtAscIdAsc(sessionId);
    }

    private WorldRoomSession session(Long sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room play session not found"));
    }

    private WorldConversation room(Long roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World room not found"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
