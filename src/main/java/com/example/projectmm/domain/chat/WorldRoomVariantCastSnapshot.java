package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "world_room_variant_cast_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_room_variant_cast_snapshot",
                columnNames = {"variant_id", "character_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomVariantCastSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private WorldRoomMessageVariant variant;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(nullable = false)
    private boolean beforeActive;

    private LocalDateTime beforeEnteredAt;
    private LocalDateTime beforeLeftAt;

    @Column(nullable = false)
    private boolean afterActive;

    private LocalDateTime afterEnteredAt;
    private LocalDateTime afterLeftAt;

    public WorldRoomVariantCastSnapshot(
            WorldRoomMessageVariant variant,
            Long characterId,
            boolean beforeActive,
            LocalDateTime beforeEnteredAt,
            LocalDateTime beforeLeftAt,
            boolean afterActive,
            LocalDateTime afterEnteredAt,
            LocalDateTime afterLeftAt
    ) {
        if (variant == null) {
            throw new IllegalArgumentException("variant must not be null");
        }
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        this.variant = variant;
        this.characterId = characterId;
        this.beforeActive = beforeActive;
        this.beforeEnteredAt = beforeEnteredAt;
        this.beforeLeftAt = beforeLeftAt;
        this.afterActive = afterActive;
        this.afterEnteredAt = afterEnteredAt;
        this.afterLeftAt = afterLeftAt;
    }
}
