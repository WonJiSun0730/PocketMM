package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRoomSessionSystemStateRepository
        extends JpaRepository<WorldRoomSessionSystemState, Long> {

    List<WorldRoomSessionSystemState> findBySessionIdOrderBySystemSortOrderAscIdAsc(Long sessionId);

    Optional<WorldRoomSessionSystemState> findBySessionIdAndSystemId(Long sessionId, Long systemId);
    void deleteBySessionRoomId(Long roomId);
}
