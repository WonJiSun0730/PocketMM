package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_catch_ups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationCatchUp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Conversation conversation;
    private LocalDateTime processedAt;
    private LocalDateTime fromTime;
    private LocalDateTime toTime;
    private long elapsedSeconds;
    private long appliedSeconds;
    private boolean capped;
    private String pauseReason;
    @Lob
    private String actions;

    public ConversationCatchUp(Conversation room, LocalDateTime processedAt, LocalDateTime from,
            LocalDateTime to, long elapsedSeconds, boolean capped, String pauseReason, String actions) {
        this.conversation = room;
        this.processedAt = processedAt;
        this.fromTime = from;
        this.toTime = to;
        this.elapsedSeconds = elapsedSeconds;
        this.appliedSeconds = java.time.Duration.between(from, to).getSeconds();
        this.capped = capped;
        this.pauseReason = pauseReason;
        this.actions = actions;
    }
}
