package com.example.projectmm.domain.scene;

import com.example.projectmm.domain.chat.Conversation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scenes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scene { // 일정 시간 동안 여러 이벤트가 발생하는 하나의 상황 컨테이너
    public Scene(
            LocalDateTime startedAt,
            String title,
            String sceneType
    ) {
        this.startedAt = startedAt;
        this.title = title;
        this.sceneType = sceneType;
        this.active = true;
    }

    public void end(
            LocalDateTime endedAt
    ) {
        if (endedAt == null) {
            throw new IllegalArgumentException(
                    "Scene end time is required"
            );
        }

        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Scene cannot end before it starts"
            );
        }

        if (!active) {
            throw new IllegalStateException(
                    "Scene is already ended"
            );
        }

        this.endedAt = endedAt;
        this.active = false;
    }

    public static Scene forConversation(
            Conversation conversation,
            LocalDateTime startedAt,
            String title,
            String sceneType
    ) {
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation is required");
        }

        Scene scene = new Scene(
                startedAt,
                title,
                sceneType
        );
        scene.conversation = conversation;
        return scene;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(nullable = false)
    private LocalDateTime startedAt; //세계 시간 상 장면 시작

    private LocalDateTime endedAt;  //종료 시간, 진행 중이면 널

    @Column(length = 100)
    private String title;   //표시용 제목

    @Column(length = 500)
    private String summary; //장면 전체 요약

    @Column(nullable = false, length = 50)
    private String sceneType;

    @Column(nullable = false)
    private boolean active; //현재 진행 중인 장면인지
}