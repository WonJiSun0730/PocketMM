package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRoomVariantCastSnapshotRepository extends JpaRepository<WorldRoomVariantCastSnapshot, Long> {

    List<WorldRoomVariantCastSnapshot> findByVariantIdOrderByCharacterIdAsc(Long variantId);

    void deleteByVariantMessageSessionIdAndVariantMessageIdGreaterThanEqual(
            Long sessionId,
            Long messageId
    );
    void deleteByVariantMessageSessionRoomId(Long roomId);
}
