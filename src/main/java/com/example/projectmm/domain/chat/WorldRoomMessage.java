package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "world_room_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorldRoomSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime editedAt;

    private Integer selectedVariantNo;

    // Null for user input and its reply. Stored on the message, not each reroll variant.
    private Integer automaticContinuationNo;
    private Long absenceReportId;

    public void markAbsenceReport(Long id) { absenceReportId = id; }

    public void markAutomaticContinuation(int consecutiveNo) {
        if (role != ChatRole.CHARACTER || consecutiveNo < 1) {
            throw new IllegalArgumentException("Automatic continuation requires a character reply and positive number");
        }
        automaticContinuationNo = consecutiveNo;
    }

    public WorldRoomMessage(
            WorldRoomSession session,
            ChatRole role,
            String content,
            LocalDateTime createdAt
    ) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        this.session = session;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.selectedVariantNo = role == ChatRole.CHARACTER ? 1 : null;
    }

    public void selectVariant(int variantNo, String content) {
        if (role != ChatRole.CHARACTER) {
            throw new IllegalStateException("Only character messages can select variants");
        }
        if (variantNo < 1) {
            throw new IllegalArgumentException("variantNo must be at least 1");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        this.selectedVariantNo = variantNo;
        this.content = content;
    }

    public void edit(String content, LocalDateTime editedAt) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (editedAt == null) {
            throw new IllegalArgumentException("editedAt must not be null");
        }
        this.content = content;
        this.editedAt = editedAt;
    }
}
