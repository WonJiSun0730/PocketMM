package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorldConversationRepository extends JpaRepository<WorldConversation, Long> {

    Optional<WorldConversation> findTopByWorldIdOrderByCreatedAtDescIdDesc(Long worldId);
}
