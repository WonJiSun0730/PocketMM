package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRoomMessageVariantRepository extends JpaRepository<WorldRoomMessageVariant, Long> {

    List<WorldRoomMessageVariant> findByMessageIdOrderByVariantNoAsc(Long messageId);

    Optional<WorldRoomMessageVariant> findByMessageIdAndVariantNo(Long messageId, int variantNo);

    @org.springframework.data.jpa.repository.Query("select v from WorldRoomMessageVariant v join v.message m "
            + "where m.session.id = :sessionId and m.id < :beforeId "
            + "and v.variantNo = m.selectedVariantNo and v.afterSceneFactsJson is not null order by m.id desc")
    List<WorldRoomMessageVariant> selectedSceneCheckpoints(
            @org.springframework.data.repository.query.Param("sessionId") Long sessionId,
            @org.springframework.data.repository.query.Param("beforeId") long beforeId,
            org.springframework.data.domain.Pageable page);

    List<WorldRoomMessageVariant> findByMessageSessionIdAndMessageIdGreaterThanEqual(Long sessionId, Long messageId);

    default void invalidateSceneFactsFrom(Long sessionId, Long messageId) {
        findByMessageSessionIdAndMessageIdGreaterThanEqual(sessionId, messageId)
                .forEach(v -> v.captureSceneFactsWorldline(null, null));
    }

    long countByMessageId(Long messageId);

    void deleteByMessageSessionIdAndMessageIdGreaterThanEqual(
            Long sessionId,
            Long messageId
    );
    void deleteByMessageSessionRoomId(Long roomId);

    void deleteByMessageSessionId(Long sessionId);
}
