package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.character.Character;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "world_room_session_cast",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_room_session_cast",
                columnNames = {"session_id", "character_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomSessionCast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorldRoomSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime enteredAt;
    private LocalDateTime leftAt;

    public WorldRoomSessionCast(WorldRoomSession session, Character character) {
        this.session = session;
        this.character = character;
        this.active = false;
    }

    public void enter(LocalDateTime at) {
        active = true;
        enteredAt = at;
        leftAt = null;
    }

    public void leave(LocalDateTime at) {
        active = false;
        leftAt = at;
    }
}
