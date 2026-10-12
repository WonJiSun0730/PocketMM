package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PocketActivityReportRepository extends JpaRepository<PocketActivityReport, Long> {
    Optional<PocketActivityReport> findTopBySessionIdOrderByIdDesc(Long sessionId);
    void deleteBySessionId(Long sessionId);
    void deleteBySessionRoomId(Long roomId);
    void deleteBySessionIdAndSourceMessageIdGreaterThanEqual(Long sessionId, Long sourceId);
}
