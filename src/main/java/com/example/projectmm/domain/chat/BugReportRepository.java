package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {
    Optional<BugReport> findByOwnerMemberIdAndRequestId(Long owner, String requestId);
    long countByOwnerMemberIdAndCreatedAtAfter(Long owner, LocalDateTime after);
    interface Metadata {
        Long getId(); Long getOwnerMemberId(); Long getSessionId(); Long getMessageId();
        Integer getVariantNo(); BugReport.Category getCategory(); BugReport.Status getStatus();
        boolean getSharedContext(); LocalDateTime getCreatedAt(); LocalDateTime getUpdatedAt();
    }
    // A projection prevents bulk decrypting report bodies when opening the inbox.
    @Query("select b.id as id, b.ownerMemberId as ownerMemberId, b.sessionId as sessionId, b.messageId as messageId, " +
           "b.variantNo as variantNo, b.category as category, b.status as status, b.sharedContext as sharedContext, " +
           "b.createdAt as createdAt, b.updatedAt as updatedAt from BugReport b " +
           "where b.id < :before and (:status is null or b.status = :status) order by b.id desc")
    List<Metadata> inbox(@Param("before") long before, @Param("status") BugReport.Status status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BugReport b where b.id = :id")
    Optional<BugReport> lockById(@Param("id") Long id);
}
