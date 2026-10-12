package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRoomSessionRepository extends JpaRepository<WorldRoomSession, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from WorldRoomSession s where s.id = :id")
    Optional<WorldRoomSession> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<WorldRoomSession> findTopByRoomIdAndMemberIdOrderByCreatedAtDescIdDesc(
            Long roomId,
            Long memberId
    );

    Optional<WorldRoomSession> findByIdAndRoomIdAndMemberId(
            Long sessionId,
            Long roomId,
            Long memberId
    );
    List<WorldRoomSession> findByMemberIdOrderBySimulatedAtDescIdDesc(Long memberId);
    List<WorldRoomSession> findByRoomIdOrderByCreatedAtDescIdDesc(Long roomId);
    void deleteByRoomId(Long roomId);
}
