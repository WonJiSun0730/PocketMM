package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    List<Conversation>
    findByCharacterIdOrderByCreatedAtDesc(Long characterId);

    Optional<Conversation>
    findTopByCharacterIdOrderByCreatedAtDesc(Long characterId);
}