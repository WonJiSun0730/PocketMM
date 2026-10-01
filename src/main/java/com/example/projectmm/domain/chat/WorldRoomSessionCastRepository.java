package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRoomSessionCastRepository extends JpaRepository<WorldRoomSessionCast, Long> {

    List<WorldRoomSessionCast> findBySessionIdAndActiveTrueOrderByEnteredAtAscIdAsc(Long sessionId);

    List<WorldRoomSessionCast> findBySessionIdOrderByIdAsc(Long sessionId);

    Optional<WorldRoomSessionCast> findBySessionIdAndCharacterId(Long sessionId, Long characterId);
    void deleteBySessionRoomId(Long roomId);
}
