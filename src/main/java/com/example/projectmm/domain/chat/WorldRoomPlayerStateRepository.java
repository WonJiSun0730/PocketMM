package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorldRoomPlayerStateRepository extends JpaRepository<WorldRoomPlayerState, Long> {
    Optional<WorldRoomPlayerState> findBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
