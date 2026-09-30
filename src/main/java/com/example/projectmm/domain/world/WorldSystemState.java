package com.example.projectmm.domain.world;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "world_system_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldSystemState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_system_id", nullable = false)
    private WorldSystem system;

    @Column(name = "min_value", nullable = false)
    private int minValue;

    @Column(name = "max_value", nullable = false)
    private int maxValue;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public WorldSystemState(WorldSystem system, int minValue, int maxValue, String label, int sortOrder) {
        this.system = system; this.minValue = minValue; this.maxValue = maxValue; this.label = label; this.sortOrder = sortOrder;
    }
}
