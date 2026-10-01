package com.example.projectmm.domain.chat;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_profile_id")
    private PlayerProfile playerProfile;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime simulatedAt;

    private LocalDateTime lastPresenceAt;

    public WorldConversation(World world, LocalDateTime simulatedAt) {
        this.world = world;
        this.createdAt = LocalDateTime.now();
        this.simulatedAt = simulatedAt;
        this.lastPresenceAt = simulatedAt;
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
