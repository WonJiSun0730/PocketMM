package com.example.projectmm.domain.character;

import com.example.projectmm.domain.simulation.StateDelta;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "character_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "character_id",
            nullable = false,
            unique = true
    )
    private Character character;

    @Column(nullable = false)
    private int energy;

    @Column(nullable = false)
    private int mood;

    @Column(nullable = false)
    private LocalDateTime simulatedAt;

    public CharacterState(
            Character character,
            int energy,
            int mood,
            LocalDateTime simulatedAt
    ) {
        this.character = character;
        this.energy = energy;
        this.mood = mood;
        this.simulatedAt = simulatedAt;
    }

    public void apply(StateDelta delta) {
        this.energy = clamp(
                this.energy + delta.energyChange()
        );

        this.mood = clamp(
                this.mood + delta.moodChange()
        );
    }

    public void advanceSimulatedAt(LocalDateTime simulatedAt) {
        this.simulatedAt = simulatedAt;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}