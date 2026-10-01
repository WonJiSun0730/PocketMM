package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorldRoomSessionRepository extends JpaRepository<WorldRoomSession, Long> {

    Optional<WorldRoomSession> findTopByRoomIdAndMemberIdOrderByCreatedAtDescIdDesc(
            Long roomId,
            Long memberId
    );

    Optional<WorldRoomSession> findByIdAndRoomIdAndMemberId(
            Long sessionId,
            Long roomId,
            Long memberId
    );
}
