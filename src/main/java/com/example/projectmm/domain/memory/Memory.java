package com.example.projectmm.domain.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.WorldRoomSession;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "memories")
@Getter
public class Memory {

    public static Memory fromConversationEvent(Character participant, Conversation room, Event event, int strength) {
        // Ownership follows the event participant; conversation identifies where the event occurred.
        Memory memory = new Memory(participant, event, event.getSummary(), event.getImportance(),
                strength, event.getNarrativeAnchor(), event.getOccurredAt());
        memory.conversation = room;
        return memory;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_event_id")
    private Event sourceEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_chat_message_id")
    private ChatMessage sourceChatMessage;

    // Pocket 기억은 기존 Conversation 기억 사이클을 공유하되 세션 귀속만 분리한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "world_room_session_id")
    private WorldRoomSession worldRoomSession;

    @Column(length = 64, unique = true)
    private String chatFingerprint;

    // 기존 행과 세계 사건 기억은 null을 유지한다. summary 압축과 별도로 원문 근거를 보존한다.
    @Column(length = 400)
    private String evidenceQuote;

    @Embedded
    private MemoryFact fact;

    @Embedded
    private SemanticMemory semanticMemory;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false)
    private int importance;

    @Column(nullable = false)
    private int strength;

    @Column(nullable = false)
    private int narrativeAnchor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryDetailLevel detailLevel;

    @Column(nullable = false)
    private LocalDateTime rememberedAt;

    @Column
    private LocalDateTime lastAccessedAt;

    @Column
    private LocalDateTime lastReactivatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Memory() {
    }

    public Memory(
            Character character,
            Event sourceEvent,
            String summary,
            int importance,
            int strength,
            LocalDateTime rememberedAt
    ) {
        this(
                character,
                sourceEvent,
                summary,
                importance,
                strength,
                0,
                rememberedAt
        );
    }

    public Memory(
            Character character,
            Event sourceEvent,
            String summary,
            int importance,
            int strength,
            int narrativeAnchor,
            LocalDateTime rememberedAt
    ) {
        this.character = character;
        this.sourceEvent = sourceEvent;
        this.summary = summary;
        this.importance = importance;
        this.strength = strength;
        this.narrativeAnchor =
                clampNarrativeAnchor(
                        narrativeAnchor
                );
        this.detailLevel = MemoryDetailLevel.DETAILED;
        this.rememberedAt = rememberedAt;
        this.lastAccessedAt = rememberedAt;
        this.lastReactivatedAt = null;
        this.createdAt = LocalDateTime.now();
    }

    private int clampNarrativeAnchor(
            int narrativeAnchor
    ) {
        return Math.max(
                0,
                Math.min(
                        10,
                        narrativeAnchor
                )
        );
    }

    public void markAccessed(LocalDateTime accessedAt) {
        if (accessedAt == null) {
            return;
        }

        if (lastAccessedAt == null || accessedAt.isAfter(lastAccessedAt)) {
            this.lastAccessedAt = accessedAt;
        }
    }

    public void reactivate(LocalDateTime reactivatedAt) {
        if (reactivatedAt == null) {
            return;
        }

        if (lastReactivatedAt == null || reactivatedAt.isAfter(lastReactivatedAt)) {
            this.lastReactivatedAt = reactivatedAt;
        }

        markAccessed(reactivatedAt);
    }


    public void compress(String compressedSummary) {
        if (compressedSummary == null || compressedSummary.isBlank()) {
            throw new IllegalArgumentException(
                    "압축된 기억의 summary는 비어 있을 수 없습니다."
            );
        }

        if (!detailLevel.canCompressFurther()) {
            return;
        }

        this.summary = compressedSummary;
        this.detailLevel = detailLevel.next();
    }

    public void attachSemanticMemory(
            SemanticMemory semanticMemory
    ) {
        if (semanticMemory == null) {
            throw new IllegalArgumentException(
                    "Semantic memory is required"
            );
        }

        if (this.semanticMemory != null) {
            throw new IllegalStateException(
                    "Semantic memory is already attached"
            );
        }

        this.semanticMemory = semanticMemory;
    }

    public static Memory fromChat(Conversation conversation, ChatMessage source, String quote,
                                  int importance, LocalDateTime at, String fingerprint) {
        return fromChat(conversation, source, quote, importance, at, fingerprint, null);
    }

    public static Memory fromChat(Conversation conversation, ChatMessage source, String quote,
                                  int importance, LocalDateTime at, String fingerprint, MemoryFact fact) {
        if (fact != null && !fact.groundedIn(quote)) throw new IllegalArgumentException("Ungrounded memory structure");
        Memory memory = new Memory(conversation.getCharacter(), null,
                "사용자가 캐릭터에게 말한 내용: \"" + quote + "\"", importance, importance * 10, at);
        memory.conversation = conversation;
        memory.sourceChatMessage = source;
        memory.chatFingerprint = fingerprint;
        memory.evidenceQuote = quote;
        memory.fact = fact;
        return memory;
    }

    public static Memory fromWorldRoomUserSpeech(
            WorldRoomSession session,
            Character character,
            String quote,
            int importance,
            LocalDateTime at,
            String fingerprint,
            MemoryFact fact
    ) {
        if (session == null || character == null) {
            throw new IllegalArgumentException("Pocket session and character are required");
        }
        if (fact != null && !fact.groundedIn(quote)) {
            throw new IllegalArgumentException("Ungrounded memory structure");
        }
        Memory memory = new Memory(
                character,
                null,
                "사용자가 캐릭터에게 말한 내용: \"" + quote + "\"",
                importance,
                importance * 10,
                at
        );
        memory.worldRoomSession = session;
        memory.chatFingerprint = fingerprint;
        memory.evidenceQuote = quote;
        memory.fact = fact;
        return memory;
    }

    public static Memory fromWorldRoom(
            WorldRoomSession session,
            Character character,
            String summary,
            int importance,
            LocalDateTime at
    ) {
        if (session == null || character == null) {
            throw new IllegalArgumentException("Pocket session and character are required");
        }
        Memory memory = new Memory(
                character,
                null,
                summary,
                importance,
                importance * 10,
                at
        );
        memory.worldRoomSession = session;
        return memory;
    }

    public static Memory fromAutomaticAction(Conversation room, ChatMessage source, String summary,
                                              int importance, LocalDateTime at) {
        // 행동은 발언으로 위장하지 않고 해당 방의 캐릭터 경험으로만 저장한다.
        Memory memory = new Memory(room.getCharacter(), null, summary, importance, importance * 10, at);
        memory.conversation = room;
        memory.sourceChatMessage = source;
        return memory;
    }
}
