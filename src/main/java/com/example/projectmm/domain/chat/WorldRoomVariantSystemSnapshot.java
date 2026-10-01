package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "world_room_variant_system_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_room_variant_system_snapshot",
                columnNames = {"variant_id", "world_system_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomVariantSystemSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private WorldRoomMessageVariant variant;

    @Column(name = "world_system_id", nullable = false)
    private Long systemId;

    @Column(name = "before_value", nullable = false)
    private int beforeValue;

    @Column(name = "after_value", nullable = false)
    private int afterValue;

    public WorldRoomVariantSystemSnapshot(
            WorldRoomMessageVariant variant,
            Long systemId,
            int beforeValue,
            int afterValue
    ) {
        if (variant == null) throw new IllegalArgumentException("variant must not be null");
        if (systemId == null) throw new IllegalArgumentException("systemId must not be null");
        this.variant = variant;
        this.systemId = systemId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }
}
