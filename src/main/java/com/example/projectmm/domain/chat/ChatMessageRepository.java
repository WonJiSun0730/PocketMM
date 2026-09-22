package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage>
    findTop20ByCharacterIdOrderByCreatedAtDesc(Long characterId);

    boolean existsByCharacterId(Long characterId);

    List<ChatMessage>
    findTop20ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    boolean existsByConversationId(Long conversationId);
}