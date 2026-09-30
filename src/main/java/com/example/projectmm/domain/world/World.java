package com.example.projectmm.domain.world;

import com.example.projectmm.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "worlds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class World {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false)
    private Member owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Lob
    @Column(name = "play_guide", columnDefinition = "TEXT")
    private String playGuide;

    public World(Member owner, String name, String description) {
        this.owner = owner;
        this.name = name;
        this.description = description;
    }

    public void updatePlayGuide(String playGuide) {
        this.playGuide = playGuide;
    }

    public void updateMetadata(String name, String description, String introduction) {
        this.name = name;
        this.description = description;
        this.introduction = introduction;
    }
}
