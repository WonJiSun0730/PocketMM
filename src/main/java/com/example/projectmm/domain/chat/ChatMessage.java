package com.example.projectmm.domain.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.domain.character.Character;
import jakarta.persistence.*;
import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentMode contentMode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ChatMessage(
            Character character,
            ChatRole role,
            String content,
            ContentMode contentMode,
            LocalDateTime createdAt
    ) {
        if (character == null) {
            throw new IllegalArgumentException("character must not be null");
        }

        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        if (contentMode == null) {
            throw new IllegalArgumentException("contentMode must not be null");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }

        this.character = character;
        this.role = role;
        this.content = content;
        this.contentMode = contentMode;
        this.createdAt = createdAt;
    }

    public ChatMessage(
            Conversation conversation,
            Character character,
            ChatRole role,
            String content,
            ContentMode contentMode,
            LocalDateTime createdAt
    ) {
        this.conversation = conversation;
        this.character = character;
        this.role = role;
        this.content = content;
        this.contentMode = contentMode;
        this.createdAt = createdAt;
    }
}