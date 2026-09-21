package com.example.projectmm.domain.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
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
}