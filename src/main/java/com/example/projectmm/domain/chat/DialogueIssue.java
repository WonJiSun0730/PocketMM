package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.time.LocalDateTime;

/** Independent evidence copy: deleting or regenerating the conversation does not delete reports. */
@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "dialogue_issues", indexes = @Index(name = "ix_dialogue_issue_owner", columnList = "owner_member_id,id"))
public class DialogueIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_member_id", nullable = false) private Long ownerMemberId;
    @Column(nullable = false) private Long sessionId;
    @Column(nullable = false) private Long messageId;
    @Column(nullable = false) private int variantNo;
    @Column(nullable = false, length = 30) private String category;
    @Column(nullable = false, columnDefinition = "TEXT") private String problem;
    @Column(nullable = false, columnDefinition = "TEXT") private String expected;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String evidenceJson;
    @Column(nullable = false) private LocalDateTime createdAt;

    public DialogueIssue(Long owner, Long session, Long message, int variant, String category,
                         String problem, String expected, String evidence, LocalDateTime at) {
        this.ownerMemberId = owner; this.sessionId = session; this.messageId = message; this.variantNo = variant;
        this.category = category; this.problem = problem; this.expected = expected;
        this.evidenceJson = evidence; this.createdAt = at;
    }
}
