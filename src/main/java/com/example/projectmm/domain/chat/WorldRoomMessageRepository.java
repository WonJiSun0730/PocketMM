package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRoomMessageRepository extends JpaRepository<WorldRoomMessage, Long> {

    List<WorldRoomMessage> findTop50BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop50BySessionIdOrderByIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop50BySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long beforeId);

    void deleteBySessionIdAndIdGreaterThanEqual(Long sessionId, Long messageId);

    java.util.Optional<WorldRoomMessage> findTopBySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long messageId);
}
