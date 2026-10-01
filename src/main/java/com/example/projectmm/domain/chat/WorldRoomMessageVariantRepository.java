package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRoomMessageVariantRepository extends JpaRepository<WorldRoomMessageVariant, Long> {

    List<WorldRoomMessageVariant> findByMessageIdOrderByVariantNoAsc(Long messageId);

    Optional<WorldRoomMessageVariant> findByMessageIdAndVariantNo(Long messageId, int variantNo);

    long countByMessageId(Long messageId);

    void deleteByMessageSessionIdAndMessageIdGreaterThanEqual(
            Long sessionId,
            Long messageId
    );
}
