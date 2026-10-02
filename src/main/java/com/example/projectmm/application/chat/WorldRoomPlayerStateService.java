package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.WorldRoomPlayerState;
import com.example.projectmm.domain.chat.WorldRoomPlayerStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldRoomPlayerStateService {

    private final WorldRoomSessionService sessions;
    private final WorldRoomPlayerStateRepository states;

    @Transactional
    public WorldRoomPlayerState current(Long sessionId, String loginId) {
        var session = sessions.pocket(sessionId, loginId);
        return states.findBySessionId(sessionId)
                .orElseGet(() -> states.save(new WorldRoomPlayerState(session)));
    }

    @Transactional
    public WorldRoomPlayerState update(
            Long sessionId,
            String loginId,
            LocalDateTime simulatedAt,
            String location,
            List<String> conditions,
            List<String> inventory
    ) {
        var session = sessions.pocket(sessionId, loginId);
        if (simulatedAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation time is required");
        }
        session.updateSimulatedAt(simulatedAt);
        var state = states.findBySessionId(sessionId)
                .orElseGet(() -> states.save(new WorldRoomPlayerState(session)));
        state.update(location, conditions, inventory);
        return state;
    }
}
