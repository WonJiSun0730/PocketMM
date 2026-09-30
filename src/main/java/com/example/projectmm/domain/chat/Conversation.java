package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.world.World;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "world_id")
    private World world;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime simulatedAt;

    // 기존 방의 null은 기존 세계 연결을 유지하고, 새로 시작한 방만 공용 상태와 분리한다.
    private Boolean independentStart;
    private Integer energy;
    private Integer mood;

    // 프로필을 공유해도 상태는 방마다 독립적이다. 기존 NPC 수치를 플레이어에게 이관하지 않는다.
    private Integer playerEnergy;
    private Integer playerMood;
    public int getPlayerEnergy() { return playerEnergy == null ? 70 : playerEnergy; }
    public int getPlayerMood() { return playerMood == null ? 70 : playerMood; }
    public void applyPlayerState(int energy, int mood) {
        playerEnergy = Math.clamp(energy, 0, 100);
        playerMood = Math.clamp(mood, 0, 100);
    }

    // 실제 접속 시각과 이야기 속 시각은 분리한다. 기존 방은 명시적으로 켜기 전까지 정지한다.
    private Boolean autoProgressEnabled;
    private LocalDateTime lastPresenceAt;
    private String progressPauseReason;
    private String chatOperationId;
    private LocalDateTime chatOperationUntil;
    private String catchUpOperationId;
    private LocalDateTime catchUpOperationUntil;

    public boolean autoProgressEnabled() { return Boolean.TRUE.equals(autoProgressEnabled); }

    public void configureAutoProgress(boolean enabled, LocalDateTime now) {
        autoProgressEnabled = enabled;
        progressPauseReason = null;
        markPresent(now);
    }

    public void markPresent(LocalDateTime now) {
        // 시스템 시계가 뒤로 움직여도 같은 구간을 다시 계산하지 않는다.
        if (lastPresenceAt == null || now.isAfter(lastPresenceAt)) lastPresenceAt = now;
    }

    public void applyRoomState(int energy, int mood, LocalDateTime at) {
        this.energy = energy;
        this.mood = mood;
        this.simulatedAt = at;
    }

    public void pauseProgress(String reason) { progressPauseReason = reason; }

    public void clearProgressPause() {
        progressPauseReason = null;
    }

    public boolean catchUpInProgress(LocalDateTime now) {
        return catchUpOperationId != null
                && catchUpOperationUntil != null
                && now.isBefore(catchUpOperationUntil);
    }

    public void beginCatchUp(String token, LocalDateTime now) {
        catchUpOperationId = token;

        // AI 요청 실패나 서버 종료로 finalize되지 못해도
        // 방이 영구적으로 잠기지 않게 한다.
        catchUpOperationUntil = now.plusMinutes(10);
    }

    public void endCatchUp(String token) {
        if (token.equals(catchUpOperationId)) {
            catchUpOperationId = null;
            catchUpOperationUntil = null;
        }
    }

    public boolean chatInProgress(LocalDateTime now) {
        return chatOperationId != null && chatOperationUntil != null && now.isBefore(chatOperationUntil);
    }

    public void beginChat(String token, LocalDateTime now) {
        chatOperationId = token;
        // 서버 종료로 정리하지 못한 요청도 영구적으로 방을 막지 않게 한다.
        chatOperationUntil = now.plusMinutes(10);
        markPresent(now);
    }

    public void endChat(String token, LocalDateTime now) {
        if (token.equals(chatOperationId)) {
            chatOperationId = null;
            chatOperationUntil = null;
            markPresent(now);
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_profile_id")
    private PlayerProfile playerProfile;

    public void selectPlayerProfile(PlayerProfile profile) {
        // 변경 전 이력은 유지한다. 과거 기억의 인물별 재분류는 후속 작업에서 다룬다.
        this.playerProfile = profile;
    }

    public boolean isIndependentStart() {
        return Boolean.TRUE.equals(independentStart);
    }

    public static Conversation forWorld(Character character, World world, LocalDateTime at) {
        Conversation conversation = new Conversation(character, at);
        conversation.world = world;
        return conversation;
    }

    public static Conversation fresh(Character character, LocalDateTime at) {
        Conversation conversation = new Conversation(character, at);
        conversation.independentStart = true;
        // 캐릭터 생성 시 기본 상태와 동일하다. 기존 CharacterState를 초기화하지 않는다.
        conversation.energy = 70;
        conversation.mood = 70;
        return conversation;
    }

    public Conversation(
            Character character,
            LocalDateTime simulatedAt
    ) {
        this.character = character;
        this.playerEnergy = 70;
        this.playerMood = 70;
        this.createdAt = LocalDateTime.now();
        this.simulatedAt = simulatedAt;
    }

    public void updateSimulatedAt(LocalDateTime simulatedAt) {
        this.simulatedAt = simulatedAt;
    }
}
