package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "world_room_intros")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomIntro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private WorldConversation room;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int sortOrder;

    public WorldRoomIntro(WorldConversation room, String title, String content, int sortOrder) {
        if (room == null) throw new IllegalArgumentException("room is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        this.room = room;
        this.title = title.trim();
        this.content = content.trim();
        this.sortOrder = sortOrder;
    }
}
