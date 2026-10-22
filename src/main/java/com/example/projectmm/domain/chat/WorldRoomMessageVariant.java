package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "world_room_message_variants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_room_message_variant",
                columnNames = {"message_id", "variant_no"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomMessageVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private WorldRoomMessage message;

    @Column(name = "variant_no", nullable = false)
    private int variantNo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime beforeSimulatedAt;

    private LocalDateTime afterSimulatedAt;

    @Column(columnDefinition = "TEXT")
    private String beforePlayerStateJson;

    @Column(columnDefinition = "TEXT")
    private String afterPlayerStateJson;

    @Column(columnDefinition = "TEXT")
    private String userSceneSetupJson;

    @Column(columnDefinition = "LONGTEXT")
    private String evaluationContextJson;

    @Column(columnDefinition = "LONGTEXT")
    private String testDiagnosticsJson;

    @Column(columnDefinition = "LONGTEXT")
    private String beforeSceneFactsJson;

    @Column(columnDefinition = "LONGTEXT")
    private String afterSceneFactsJson;

    public void captureSceneFactsWorldline(String before, String after) {
        this.beforeSceneFactsJson = before;
        this.afterSceneFactsJson = after;
    }

    public void captureTestDiagnostics(String json) { this.testDiagnosticsJson = json; }

    @Column(columnDefinition = "LONGTEXT")
    private String testMemoryBeforeJson;

    @Column(columnDefinition = "LONGTEXT")
    private String testMemoryAfterJson;

    public void captureTestMemoryBefore(String json) { this.testMemoryBeforeJson = json; }
    public void captureTestMemoryAfter(String json) { this.testMemoryAfterJson = json; }

    public void captureEvaluationContext(String json) { this.evaluationContextJson = json; }

    public void captureUserSceneSetup(String json) {
        this.userSceneSetupJson = json;
    }

    public void capturePlayerStateWorldline(String before, String after) {
        this.beforePlayerStateJson = before;
        this.afterPlayerStateJson = after;
    }

    public void editContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        this.content = content;
    }

    public WorldRoomMessageVariant(
            WorldRoomMessage message,
            int variantNo,
            String content,
            LocalDateTime createdAt
    ) {
        this(message, variantNo, content, createdAt, null, null);
    }

    public WorldRoomMessageVariant(
            WorldRoomMessage message,
            int variantNo,
            String content,
            LocalDateTime createdAt,
            LocalDateTime beforeSimulatedAt,
            LocalDateTime afterSimulatedAt
    ) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (variantNo < 1) {
            throw new IllegalArgumentException("variantNo must be at least 1");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        this.message = message;
        this.variantNo = variantNo;
        this.content = content;
        this.createdAt = createdAt;
        this.beforeSimulatedAt = beforeSimulatedAt;
        this.afterSimulatedAt = afterSimulatedAt;
    }

    public void captureTimeWorldline(
            LocalDateTime beforeSimulatedAt,
            LocalDateTime afterSimulatedAt
    ) {
        if (beforeSimulatedAt == null || afterSimulatedAt == null) {
            throw new IllegalArgumentException("Pocket time snapshot must not be null");
        }
        this.beforeSimulatedAt = beforeSimulatedAt;
        this.afterSimulatedAt = afterSimulatedAt;
    }
}
