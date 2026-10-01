package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.world.World;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "world_conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldConversation {

    public enum Visibility {
        PRIVATE,
        PUBLIC
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_member_id")
    private Member owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PRIVATE;


    @Column(nullable = false)
    private LocalDateTime createdAt;


    public WorldConversation(World world, LocalDateTime createdAt) {
        this(world, null, world.getName(), Visibility.PRIVATE, createdAt);
    }

    public WorldConversation(
            World world,
            Member owner,
            String title,
            Visibility visibility,
            LocalDateTime createdAt
    ) {
        this.world = world;
        this.owner = owner;
        this.title = normalizeTitle(title, world.getName());
        this.visibility = visibility == null ? Visibility.PRIVATE : visibility;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public void updateRoom(String title, Visibility visibility) {
        this.title = normalizeTitle(title, world.getName());
        this.visibility = visibility == null ? Visibility.PRIVATE : visibility;
    }

    private static String normalizeTitle(String title, String fallback) {
        if (title == null || title.isBlank()) {
            return fallback;
        }
        String normalized = title.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

}
