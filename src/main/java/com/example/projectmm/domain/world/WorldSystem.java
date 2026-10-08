package com.example.projectmm.domain.world;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "world_systems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldSystem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;

    @Column(nullable = false, length = 100)
    private String name;

    @Lob @Column(name = "change_condition", nullable = false, columnDefinition = "TEXT")
    private String changeCondition;

    @Column(name = "min_value", nullable = false)
    private int minValue;

    @Column(name = "max_value", nullable = false)
    private int maxValue;

    @Column(name = "initial_value", nullable = false)
    private int initialValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived;

    public void archive() { archived = true; }

    public WorldSystem(
            World world,
            String name,
            String changeCondition,
            int minValue,
            int maxValue,
            int initialValue,
            int sortOrder
    ) {
        if (initialValue < minValue || initialValue > maxValue) {
            throw new IllegalArgumentException("System initial value is outside system bounds.");
        }
        this.world = world;
        this.name = name;
        this.changeCondition = changeCondition;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.initialValue = initialValue;
        this.sortOrder = sortOrder;
    }
}
