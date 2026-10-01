package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime editedAt;

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
