package com.example.projectmm.domain.relationship;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "relationship_facts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_relationship_fact_dimension",
                        columnNames = {
                                "from_character_id",
                                "to_character_id",
                                "relation"
                        }
                )
        }
)
public class RelationshipFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_character_id", nullable = false)
    private Character fromCharacter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_character_id", nullable = false)
    private Character toCharacter;

    @Column(nullable = false, length = 120)
    private String relation;

    @Column(name = "fact_value", nullable = false, length = 400)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_event_id")
    private Event sourceEvent;

    @Column(nullable = false)
    private LocalDateTime establishedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected RelationshipFact() {
    }

    public RelationshipFact(
            Character fromCharacter,
            Character toCharacter,
            String relation,
            String value,
            Event sourceEvent,
            LocalDateTime establishedAt
    ) {
        if (fromCharacter == null) {
            throw new IllegalArgumentException(
                    "fromCharacter is required"
            );
        }

        if (toCharacter == null) {
            throw new IllegalArgumentException(
                    "toCharacter is required"
            );
        }

        if (relation == null || relation.isBlank()) {
            throw new IllegalArgumentException(
                    "relation is required"
            );
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "value is required"
            );
        }

        if (establishedAt == null) {
            throw new IllegalArgumentException(
                    "establishedAt is required"
            );
        }

        this.fromCharacter = fromCharacter;
        this.toCharacter = toCharacter;
        this.relation = relation.strip();
        this.value = value.strip();
        this.sourceEvent = sourceEvent;
        this.establishedAt = establishedAt;
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

    public String getRelation() {
        return relation;
    }

    public String getValue() {
        return value;
    }

    public void update(
            String value,
            Event sourceEvent,
            LocalDateTime establishedAt
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "value is required"
            );
        }

        if (establishedAt == null) {
            throw new IllegalArgumentException(
                    "establishedAt is required"
            );
        }

        this.value = value.strip();
        this.sourceEvent = sourceEvent;
        this.establishedAt = establishedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public Event getSourceEvent() {
        return sourceEvent;
    }

    public LocalDateTime getEstablishedAt() {
        return establishedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}