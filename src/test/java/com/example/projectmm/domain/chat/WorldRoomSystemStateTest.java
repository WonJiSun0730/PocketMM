package com.example.projectmm.domain.chat;

import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldSystem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class WorldRoomSystemStateTest {

    @Test
    void sessionSystemStateStartsFromInitialValueAndClampsDeltas() {
        var world = new World(null, "테스트 월드", null);
        var system = new WorldSystem(world, "피에타", "행동에 따라 변화", 0, 100, 100, 0);
        var state = new WorldRoomSessionSystemState(null, system, system.getInitialValue());

        assertThat(state.getCurrentValue()).isEqualTo(100);

        state.applyDelta(-8);
        assertThat(state.getCurrentValue()).isEqualTo(92);

        state.applyDelta(20);
        assertThat(state.getCurrentValue()).isEqualTo(100);

        state.applyDelta(-150);
        assertThat(state.getCurrentValue()).isZero();
    }

    @Test
    void variantSystemSnapshotKeepsIndependentBeforeAndAfterValues() throws Exception {
        var variant = new WorldRoomMessageVariant(null, 1, "응답", java.time.LocalDateTime.now());
        setId(variant, 10L);

        var snapshot = new WorldRoomVariantSystemSnapshot(
                variant,
                3L,
                100,
                92
        );

        assertThat(snapshot.getVariant()).isSameAs(variant);
        assertThat(snapshot.getSystemId()).isEqualTo(3L);
        assertThat(snapshot.getBeforeValue()).isEqualTo(100);
        assertThat(snapshot.getAfterValue()).isEqualTo(92);
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
