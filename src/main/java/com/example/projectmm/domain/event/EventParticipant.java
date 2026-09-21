package com.example.projectmm.domain.event;

import com.example.projectmm.domain.character.Character;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "event_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventParticipant {
    public EventParticipant(
            Event event,
            Character character,
            String role
    ) {
        this.event = event;
        this.character = character;
        this.role = role;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    //EventParticipant를 DB에서 읽었다고 Character와 Event 전체를 무조건 즉시 같이 읽어오지 말자.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Column(nullable = false, length = 30)
    private String role;
}