package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.player.PlayerProfile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "world_room_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private WorldConversation room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_profile_id")
    private PlayerProfile playerProfile;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime simulatedAt;

    private LocalDateTime lastPresenceAt;

    public WorldRoomSession(
            WorldConversation room,
            Member member,
            PlayerProfile playerProfile,
            LocalDateTime at
    ) {
        this.room = room;
        this.member = member;
        this.playerProfile = playerProfile;
        this.createdAt = at;
        this.simulatedAt = at;
        this.lastPresenceAt = at;
    }

    public void selectPlayerProfile(PlayerProfile playerProfile) {
        this.playerProfile = playerProfile;
    }

    public void updateSimulatedAt(LocalDateTime at) {
        this.simulatedAt = at;
    }

    public void markPresent(LocalDateTime at) {
        if (lastPresenceAt == null || at.isAfter(lastPresenceAt)) {
            lastPresenceAt = at;
        }
    }
}
