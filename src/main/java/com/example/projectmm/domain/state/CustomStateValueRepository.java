package com.example.projectmm.domain.state;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomStateValueRepository extends JpaRepository<CustomStateValue, Long> {
    List<CustomStateValue> findByConversationIdOrderByIdAsc(Long conversationId);

    Optional<CustomStateValue> findByConversationIdAndDefinitionId(Long conversationId, Long definitionId);
}
