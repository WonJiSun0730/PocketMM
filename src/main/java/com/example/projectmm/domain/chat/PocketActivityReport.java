package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pocket_activity_reports", indexes = @Index(name = "ix_pocket_activity_session", columnList = "session_id,id"))
public class PocketActivityReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false)
    private WorldRoomSession session;
    private Long sourceMessageId;
    @Column(nullable = false) private LocalDateTime fromTime;
    @Column(nullable = false) private LocalDateTime toTime;
    @Column(nullable = false) private LocalDateTime processedAt;
    private boolean capped;
    @Column(length = 300) private String pauseReason;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String activitiesJson;
    @Column(columnDefinition = "LONGTEXT") private String narrativeContextJson;
    @Column(columnDefinition = "LONGTEXT") private String narrativeText;
    @Column(length = 36) private String narrativeOperationId;
    private LocalDateTime narrativeOperationUntil;
    public void captureNarrativeContext(String json) { narrativeContextJson = json; }
    public boolean narrativeInProgress(LocalDateTime now) {
        return narrativeOperationId != null && narrativeOperationUntil != null && narrativeOperationUntil.isAfter(now);
    }
    public void claimNarrative(String token, LocalDateTime until) {
        narrativeOperationId = token; narrativeOperationUntil = until;
    }
    public void finishNarrative(String token, String text) {
        if (!java.util.Objects.equals(token,narrativeOperationId)) throw new IllegalStateException("Narrative claim changed");
        narrativeText = text; narrativeOperationId = null; narrativeOperationUntil = null;
    }
    public PocketActivityReport(WorldRoomSession session, LocalDateTime from, LocalDateTime to,
            LocalDateTime processedAt, boolean capped, String reason, String json) {
        this.session = session; fromTime = from; toTime = to; this.processedAt = processedAt;
        this.capped = capped; pauseReason = reason; activitiesJson = json;
    }
    public void linkMessage(Long id) { sourceMessageId = id; }
}
