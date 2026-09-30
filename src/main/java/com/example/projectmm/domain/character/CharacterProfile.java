package com.example.projectmm.domain.character;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "character_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterProfile {

    public CharacterProfile(
            Character character,
            String description,
            String rawDescription,
            String playGuide,
            String exampleDialogue,
            String personality,
            String speechStyle,
            String background,
            String appearance
    ) {
        this.character = character;
        this.description = description;
        this.rawDescription = rawDescription;
        this.playGuide = playGuide;
        this.exampleDialogue = exampleDialogue;
        this.personality = personality;
        this.speechStyle = speechStyle;
        this.background = background;
        this.appearance = appearance;
    }

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

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "raw_description", columnDefinition = "TEXT")
    private String rawDescription;

    @Lob
    @Column(name = "play_guide", columnDefinition = "TEXT")
    private String playGuide;

    @Lob
    @Column(name = "example_dialogue", columnDefinition = "TEXT")
    private String exampleDialogue;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String personality;

    @Lob
    @Column(name = "speech_style", columnDefinition = "TEXT")
    private String speechStyle;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String background;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String appearance;

    public void update(
            String description,
            String rawDescription,
            String playGuide,
            String exampleDialogue,
            String personality,
            String speechStyle,
            String background,
            String appearance
    ) {
        this.description = description;
        this.rawDescription = rawDescription;
        this.playGuide = playGuide;
        this.exampleDialogue = exampleDialogue;
        this.personality = personality;
        this.speechStyle = speechStyle;
        this.background = background;
        this.appearance = appearance;
    }
}