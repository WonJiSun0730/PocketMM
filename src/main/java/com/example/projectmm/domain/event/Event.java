package com.example.projectmm.domain.event;

import com.example.projectmm.domain.scene.Scene;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event { //세계에서 실제로 무슨 일이 일어났는가 - 씬이 이벤트를 여러 개 들게 됨
    public Event(
            Scene scene,
            LocalDateTime occurredAt,
            String eventType,
            String summary,
            int importance
    ) {
        this(
                scene,
                occurredAt,
                eventType,
                summary,
                importance,
                0
        );
    }

    public Event(
            Scene scene,
            LocalDateTime occurredAt,
            String eventType,
            String summary,
            int importance,
            int narrativeAnchor
    ) {
        this.scene = scene;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.summary = summary;
        this.importance = importance;
        this.narrativeAnchor =
                clampNarrativeAnchor(
                        narrativeAnchor
                );
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt; //세계 내 사건 발생 시간

    @Column(nullable = false, length = 50)
    private String eventType;               //사건 종류

    @Column(nullable = false, length = 500)
    private String summary;                 //사건 기록

    @Column(nullable = false)
    private int importance;                 //기억 후보/요약 등에 사용할 중요도

    @Column(nullable = false)
    private int narrativeAnchor;

    @Column(nullable = false)
    private LocalDateTime createdAt;        //서버 데이터 기록 시간

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private Scene scene;
}