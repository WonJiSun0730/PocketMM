package com.example.projectmm.domain.character;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "character_examples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int sortOrder;

    public CharacterExample(
            Character character,
            String content,
            int sortOrder
    ) {
        this.character = character;
        this.content = content;
        this.sortOrder = sortOrder;
    }
}