package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRoomMessageRepository extends JpaRepository<WorldRoomMessage, Long> {

    java.util.Optional<WorldRoomMessage> findTopBySessionIdOrderByIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop50BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop50BySessionIdOrderByIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop15BySessionIdOrderByIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop15BySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long beforeId);

    List<WorldRoomMessage> findTop50BySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long beforeId);

    List<WorldRoomMessage> findBySessionIdAndRoleAndIdLessThanOrderByIdDesc(
            Long sessionId, ChatRole role, Long beforeId, org.springframework.data.domain.Pageable page);

    List<WorldRoomMessage> findBySessionIdAndIdGreaterThanEqualAndIdLessThanOrderByIdAsc(
            Long sessionId, Long firstId, Long beforeId);

    void deleteBySessionIdAndIdGreaterThanEqual(Long sessionId, Long messageId);

    boolean existsBySessionIdAndIdGreaterThanAndAbsenceReportIdIsNull(Long sessionId, Long messageId);

    boolean existsBySessionIdAndIdGreaterThan(Long sessionId, Long messageId);

    java.util.Optional<WorldRoomMessage> findTopBySessionIdAndIdGreaterThanEqualAndRoleOrderByIdAsc(
            Long sessionId,
            Long messageId,
            ChatRole role
    );

    java.util.Optional<WorldRoomMessage> findTopBySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long messageId);
    void deleteBySessionRoomId(Long roomId);

    void deleteBySessionId(Long sessionId);
}
