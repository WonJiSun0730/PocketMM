package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.player.PlayerProfileRepository;
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
    private final MemberRepository members;
    private final PlayerProfileRepository players;

    @Transactional
    public WorldRoomSession start(Long roomId, String loginId, Long playerProfileId) {
        var room = room(roomId);
        var member = members.findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        var player = playerProfileId == null ? null : players.findById(playerProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player profile not found"));
        return sessions.save(new WorldRoomSession(room, member, player, now()));
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
