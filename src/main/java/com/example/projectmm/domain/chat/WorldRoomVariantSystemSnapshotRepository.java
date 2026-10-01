package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRoomVariantSystemSnapshotRepository
        extends JpaRepository<WorldRoomVariantSystemSnapshot, Long> {

    List<WorldRoomVariantSystemSnapshot> findByVariantIdOrderBySystemIdAsc(Long variantId);

    void deleteByVariantMessageSessionIdAndVariantMessageIdGreaterThanEqual(
            Long sessionId,
            Long messageId
    );
}
