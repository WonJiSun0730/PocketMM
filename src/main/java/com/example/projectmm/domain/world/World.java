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

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String hashtags;

    @Column(name = "published_at")
    private java.time.LocalDateTime publishedAt;

    public void updateHashtags(String hashtags) { this.hashtags = hashtags; }

    public void publish() {
        if (publishedAt == null) publishedAt = java.time.LocalDateTime.now();
    }

    public void unpublish() { publishedAt = null; }

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

    public void updateCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public void updateMetadata(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
