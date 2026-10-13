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

    private LocalDateTime initialSimulatedAt;

    private Boolean initialTimeConfigured;
    private Boolean initialSceneResolved;

    @Column(length = 100)
    private String introTitleSnapshot;

    @Column(columnDefinition = "TEXT")
    private String introContentSnapshot;

    @Column(length = 200)
    private String initialLocation;

    @ElementCollection
    @CollectionTable(name = "world_room_session_initial_conditions", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "condition_text", length = 300)
    private java.util.List<String> initialConditions = new java.util.ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "world_room_session_initial_inventory", joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "item_text", length = 300)
    private java.util.List<String> initialInventory = new java.util.ArrayList<>();

    private LocalDateTime lastPresenceAt;
    private LocalDateTime lastProgressedAt;
    private Boolean timeCheckEnabled;
    private Integer minimumAbsenceMinutes;
    public int minimumAbsenceMinutes() { return minimumAbsenceMinutes == null ? 15 : minimumAbsenceMinutes; }
    public void configureMinimumAbsence(int minutes) { minimumAbsenceMinutes = minutes; }
    private String progressOperationId;
    private LocalDateTime progressOperationUntil;
    @Column(length = 300)
    private String progressPauseReason;

    public boolean isTimeCheckEnabled() { return Boolean.TRUE.equals(timeCheckEnabled); }
    public boolean progressInProgress(LocalDateTime now) {
        return progressOperationId != null && progressOperationUntil != null && now.isBefore(progressOperationUntil);
    }
    public void configureTimeCheck(boolean enabled, LocalDateTime now) {
        timeCheckEnabled = enabled;
        progressPauseReason = null;
        markPresent(now);
    }
    public void clearProgressPause() { progressPauseReason = null; }
    public void beginProgress(String token, LocalDateTime now) {
        progressOperationId = token;
        progressOperationUntil = now.plusMinutes(10);
    }
    public void finishProgress(String token, String pauseReason, LocalDateTime presence) {
        if (!java.util.Objects.equals(progressOperationId, token)) throw new IllegalStateException("Progress lease changed");
        progressOperationId = null;
        progressOperationUntil = null;
        progressPauseReason = pauseReason;
        if (presence != null) markPresent(presence);
    }

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
        this.initialSimulatedAt = at;
        this.initialTimeConfigured = intro.getInitialSimulatedAt() != null;
        this.initialSceneResolved = intro.getInitialSimulatedAt() != null && intro.getInitialLocation() != null
                && !intro.getInitialConditions().isEmpty() && !intro.getInitialInventory().isEmpty();
        this.introTitleSnapshot = intro.getTitle();
        this.introContentSnapshot = intro.getContent();
        this.initialLocation = intro.getInitialLocation();
        this.initialConditions.addAll(intro.getInitialConditions());
        this.initialInventory.addAll(intro.getInitialInventory());
        this.lastPresenceAt = at;
    }

    public void selectPlayerProfile(PlayerProfile playerProfile) {
        this.playerProfile = playerProfile;
    }

    public void completeInitialScene(com.example.projectmm.domain.dialogue.WorldRoomInitialScene proposed) {
        if (!Boolean.FALSE.equals(initialSceneResolved)) return;
        if (proposed != null) {
            if (!Boolean.TRUE.equals(initialTimeConfigured) && proposed.simulatedAt() != null) {
                initialSimulatedAt = proposed.simulatedAt().withSecond(0).withNano(0);
                simulatedAt = initialSimulatedAt;
            }
            if (initialLocation == null && proposed.location() != null && !proposed.location().isBlank()
                    && proposed.location().trim().length() <= 200) initialLocation = proposed.location().trim();
            if (initialConditions.isEmpty()) initialConditions.addAll(validInitialItems(proposed.conditions()));
            if (initialInventory.isEmpty()) initialInventory.addAll(validInitialItems(proposed.inventory()));
        }
        initialSceneResolved = true;
    }

    private java.util.List<String> validInitialItems(java.util.List<String> items) {
        return items == null ? java.util.List.of() : items.stream()
                .filter(item -> item != null && !item.isBlank() && item.trim().length() <= 300)
                .map(String::trim).distinct().limit(50).toList();
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

    public void markProgressed(LocalDateTime at) {
        if (lastProgressedAt == null || at.isAfter(lastProgressedAt)) lastProgressedAt = at;
    }

    public void markPresent(LocalDateTime at) {
        if (lastPresenceAt == null || at.isAfter(lastPresenceAt)) {
            lastPresenceAt = at;
        }
    }
}
