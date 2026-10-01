package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRoomMessageRepository extends JpaRepository<WorldRoomMessage, Long> {

    List<WorldRoomMessage> findTop50BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);
}
