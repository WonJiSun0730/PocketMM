package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConversationCatchUpRepository extends JpaRepository<ConversationCatchUp, Long> {
    Optional<ConversationCatchUp> findTopByConversationIdOrderByIdDesc(Long conversationId);
}
