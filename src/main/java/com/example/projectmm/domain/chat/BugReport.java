package com.example.projectmm.domain.chat;

import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** User-submitted support copy, independent of chat/QA deletion. Private text remains encrypted. */
@Entity
@Table(name = "bug_reports",
        indexes = {@Index(name = "ix_bug_report_status", columnList = "status,id"),
                   @Index(name = "ix_bug_report_owner_created", columnList = "owner_member_id,created_at")},
        uniqueConstraints = @UniqueConstraint(name = "uk_bug_report_request", columnNames = {"owner_member_id","request_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BugReport {
    public enum Category { TIME, CAST, CHARACTER, PLAYER_CONTROL, PROSE, OOC, OTHER }
    public enum Status { NEW, REVIEWING, RESOLVED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_member_id", nullable = false) private Long ownerMemberId;
    @Column(name = "request_id", nullable = false, length = 36) private String requestId;
    @Column(nullable = false) private Long sessionId;
    private Long messageId;
    private Integer variantNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.NEW;
    @Convert(converter = EncryptedTextConverter.class) @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String problem;
    @Convert(converter = EncryptedTextConverter.class) @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String expected;
    @Convert(converter = EncryptedTextConverter.class) @Column(columnDefinition = "LONGTEXT")
    private String evidenceJson;
    @Column(nullable = false) private boolean sharedContext;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedByMemberId;

    public BugReport(Long owner, String requestId, Long session, Long message, Integer variant,
                     Category category, String problem, String expected, String evidence, LocalDateTime at) {
        this.ownerMemberId=owner; this.requestId=requestId; this.sessionId=session; this.messageId=message;
        this.variantNo=variant; this.category=category; this.problem=problem; this.expected=expected;
        this.evidenceJson=evidence; this.sharedContext=evidence!=null; this.createdAt=at;
    }
    public void updateStatus(Status status, Long operatorId, LocalDateTime at) {
        this.status=status; this.updatedByMemberId=operatorId; this.updatedAt=at;
    }
}
