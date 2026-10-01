package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.member.MemberRepository;
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
    private final WorldSystemRepository worldSystems;
    private final MemberRepository members;
    private final PlayerProfileRepository players;
    private final WorldRoomIntroRepository intros;

    @Transactional
    public WorldRoomSession start(Long roomId, String loginId, Long playerProfileId, Long introId) {
        var room = room(roomId);
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
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
    public WorldRoomSession latest(Long roomId, String loginId) {
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return sessions.findTopByRoomIdAndMemberIdOrderByCreatedAtDescIdDesc(roomId, member.getId())
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
