package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.character.Character;
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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime simulatedAt;

    public Conversation(
            Character character,
            LocalDateTime simulatedAt
    ) {
        this.character = character;
        this.createdAt = LocalDateTime.now();
        this.simulatedAt = simulatedAt;
    }

    public void updateSimulatedAt(LocalDateTime simulatedAt) {
        this.simulatedAt = simulatedAt;
    }
}