package com.example.projectmm.domain.scene;

import com.example.projectmm.domain.character.Character;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "scene_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SceneParticipant { //이 장면에 누가 있는가
    public SceneParticipant(Scene scene, Character character) {
        this.scene = scene;
        this.character = character;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;
}