package com.example.projectmm.domain.world;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "world_lore_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldLoreEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorldLoreCategory category;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int sortOrder;

    public WorldLoreEntry(World world, WorldLoreCategory category, String content, int sortOrder) {
        this.world = world;
        this.category = category;
        this.content = content;
        this.sortOrder = sortOrder;
    }
}
