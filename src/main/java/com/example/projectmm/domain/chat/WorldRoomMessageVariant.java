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
