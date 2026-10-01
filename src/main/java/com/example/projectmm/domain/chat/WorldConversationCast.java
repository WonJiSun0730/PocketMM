package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.character.Character;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "world_conversation_cast",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_conversation_cast",
                columnNames = {"world_conversation_id", "character_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldConversationCast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_conversation_id", nullable = false)
    private WorldConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    public WorldConversationCast(
            WorldConversation conversation,
            Character character,
            LocalDateTime joinedAt
    ) {
        this.conversation = conversation;
        this.character = character;
        this.joinedAt = joinedAt;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public void enter(LocalDateTime at) {
        joinedAt = at;
        leftAt = null;
    }

    public void leave(LocalDateTime at) {
        leftAt = at;
    }
}
