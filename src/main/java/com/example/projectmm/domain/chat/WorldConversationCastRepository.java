package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldConversationCastRepository
        extends JpaRepository<WorldConversationCast, Long> {

    List<WorldConversationCast>
    findByConversationIdOrderByJoinedAtAscIdAsc(Long conversationId);


    Optional<WorldConversationCast>
    findByConversationIdAndCharacterId(Long conversationId, Long characterId);
}
