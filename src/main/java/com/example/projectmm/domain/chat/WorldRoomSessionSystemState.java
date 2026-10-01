package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.world.WorldSystem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "world_room_session_system_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_session_system",
                columnNames = {"session_id", "world_system_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomSessionSystemState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorldRoomSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_system_id", nullable = false)
    private WorldSystem system;

    @Column(name = "current_value", nullable = false)
    private int currentValue;

    public WorldRoomSessionSystemState(
            WorldRoomSession session,
            WorldSystem system,
            int currentValue
    ) {
        this.session = session;
        this.system = system;
        this.currentValue = clamp(system, currentValue);
    }

    public void setCurrentValue(int value) {
        this.currentValue = clamp(system, value);
    }

    public void applyDelta(int delta) {
        setCurrentValue(currentValue + delta);
    }

    private static int clamp(WorldSystem system, int value) {
        return Math.max(system.getMinValue(), Math.min(system.getMaxValue(), value));
    }
}
