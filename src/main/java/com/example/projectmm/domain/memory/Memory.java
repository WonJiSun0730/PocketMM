package com.example.projectmm.domain.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.Conversation;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "memories")
@Getter
public class Memory {

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

    @Column(length = 64, unique = true)
    private String chatFingerprint;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false)
    private int importance;

    @Column(nullable = false)
    private int strength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryDetailLevel detailLevel;

    @Column(nullable = false)
    private LocalDateTime rememberedAt;

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
        this.character = character;
        this.sourceEvent = sourceEvent;
        this.summary = summary;
        this.importance = importance;
        this.strength = strength;
        this.detailLevel = MemoryDetailLevel.DETAILED;
        this.rememberedAt = rememberedAt;
        this.createdAt = LocalDateTime.now();
    }

    public void compress(String compressedSummary) {
        if (compressedSummary == null || compressedSummary.isBlank()) {
            throw new IllegalArgumentException(
                    "압축된 기억의 summary는 비어 있을 수 없습니다."
            );
        }

        this.summary = compressedSummary;
        this.detailLevel = MemoryDetailLevel.COMPRESSED;
    }

    public static Memory fromChat(Conversation conversation, ChatMessage source, String quote,
                                  int importance, LocalDateTime at, String fingerprint) {
        Memory memory = new Memory(conversation.getCharacter(), null,
                "사용자가 캐릭터에게 말한 내용: \"" + quote + "\"", importance, importance * 10, at);
        memory.conversation = conversation;
        memory.sourceChatMessage = source;
        memory.chatFingerprint = fingerprint;
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
