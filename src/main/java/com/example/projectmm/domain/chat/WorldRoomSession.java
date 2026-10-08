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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intro_id", nullable = false)
    private WorldRoomIntro intro;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime simulatedAt;

    private LocalDateTime lastPresenceAt;

    // Null on pre-snapshot pockets; their original room roster remains the fallback.
    private Boolean castRosterCaptured;

    public void markCastRosterCaptured() {
        this.castRosterCaptured = true;
    }

    @Column(columnDefinition = "TEXT")
    private String userNote;

    @Column(columnDefinition = "TEXT")
    private String pocketPlayGuide;

    @Column(nullable = false)
    private boolean noControlPlayer = false;

    @Column(nullable = false, length = 20)
    private String aiProvider = "GEMINI";

    @Column(length = 80)
    private String aiModel;

    public WorldRoomSession(
            WorldConversation room,
            Member member,
            PlayerProfile playerProfile,
            WorldRoomIntro intro,
            LocalDateTime at
    ) {
        if (intro == null || intro.getRoom() == null || !intro.getRoom().equals(room)) {
            throw new IllegalArgumentException("intro must belong to the room");
        }
        this.room = room;
        this.member = member;
        this.playerProfile = playerProfile;
        this.intro = intro;
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

    public void updatePocketSettings(String userNote, String pocketPlayGuide, boolean noControlPlayer) {
        updatePocketSettings(userNote, pocketPlayGuide, noControlPlayer, aiProvider, aiModel);
    }

    public void updatePocketSettings(
            String userNote,
            String pocketPlayGuide,
            boolean noControlPlayer,
            String aiProvider,
            String aiModel
    ) {
        this.userNote = normalizeOptionalText(userNote);
        this.pocketPlayGuide = normalizeOptionalText(pocketPlayGuide);
        this.noControlPlayer = noControlPlayer;
        this.aiProvider = normalizeProvider(aiProvider);
        this.aiModel = normalizeOptionalText(aiModel);
    }

    private String normalizeProvider(String value) {
        if (value == null || value.isBlank()) return "GEMINI";
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.equals("GEMINI") && !normalized.equals("OPENAI")) {
            throw new IllegalArgumentException("Unsupported AI provider: " + value);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public void markPresent(LocalDateTime at) {
        if (lastPresenceAt == null || at.isAfter(lastPresenceAt)) {
            lastPresenceAt = at;
        }
    }
}
