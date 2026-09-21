package com.example.projectmm.domain.relationship;

import com.example.projectmm.domain.character.Character;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "relationships")
public class Relationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_character_id", nullable = false)
    private Character fromCharacter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_character_id", nullable = false)
    private Character toCharacter;

    @Column(nullable = false)
    private int affinity;

    @Column(nullable = false)
    private int trust;

    @Column(nullable = false)
    private int tension;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Relationship() {
    }

    public Relationship(
            Character fromCharacter,
            Character toCharacter
    ) {
        this.fromCharacter = fromCharacter;
        this.toCharacter = toCharacter;
        this.affinity = 0;
        this.trust = 0;
        this.tension = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Character getFromCharacter() {
        return fromCharacter;
    }

    public Character getToCharacter() {
        return toCharacter;
    }

    public int getAffinity() {
        return affinity;
    }

    public int getTrust() {
        return trust;
    }

    public int getTension() {
        return tension;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void apply(
            RelationshipDelta delta
    ) {
        this.affinity =
                clamp(affinity + delta.affinityChange());

        this.trust =
                clamp(trust + delta.trustChange());

        this.tension =
                clamp(tension + delta.tensionChange());

        this.updatedAt = LocalDateTime.now();
    }

    private int clamp(int value) {
        return Math.max(
                0,
                Math.min(100, value)
        );
    }
}