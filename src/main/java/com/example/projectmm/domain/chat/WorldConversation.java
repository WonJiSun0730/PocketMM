package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.player.PlayerProfile;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_profile_id")
    private PlayerProfile playerProfile;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime simulatedAt;

    private LocalDateTime lastPresenceAt;

    public WorldConversation(World world, LocalDateTime simulatedAt) {
        this(world, null, world.getName(), Visibility.PRIVATE, simulatedAt);
    }

    public WorldConversation(
            World world,
            Member owner,
            String title,
            Visibility visibility,
            LocalDateTime simulatedAt
    ) {
        this.world = world;
        this.owner = owner;
        this.title = normalizeTitle(title, world.getName());
        this.visibility = Visibility.PRIVATE;
        this.createdAt = LocalDateTime.now();
        this.simulatedAt = simulatedAt;
        this.lastPresenceAt = simulatedAt;
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

    public void selectPlayerProfile(PlayerProfile profile) {
        this.playerProfile = profile;
    }

    public void updateSimulatedAt(LocalDateTime simulatedAt) {
        this.simulatedAt = simulatedAt;
    }

    public void markPresent(LocalDateTime now) {
        if (lastPresenceAt == null || now.isAfter(lastPresenceAt)) {
            lastPresenceAt = now;
        }
    }
}
