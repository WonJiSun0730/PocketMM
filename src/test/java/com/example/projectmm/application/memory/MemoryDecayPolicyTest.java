package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryDecayPolicyTest {

    private final MemoryDecayPolicy policy =
            new MemoryDecayPolicy();

    @Test
    void 보통_중요도의_기억은_시간에_따라_감쇠한다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        6,
                        70
                );

        MemoryDecayResult result =
                policy.calculate(
                        memory,
                        rememberedAt.plusDays(10)
                );

        assertThat(result.originalStrength())
                .isEqualTo(70);

        assertThat(result.effectiveStrength())
                .isEqualTo(50);

        assertThat(result.compressionRequired())
                .isFalse();
    }

    @Test
    void 중요한_기억은_더_천천히_감쇠한다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        9,
                        90
                );

        MemoryDecayResult result =
                policy.calculate(
                        memory,
                        rememberedAt.plusDays(10)
                );

        assertThat(result.effectiveStrength())
                .isEqualTo(80);

        assertThat(result.compressionRequired())
                .isFalse();
    }

    @Test
    void 강도가_충분히_낮아지면_압축_대상이_된다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        5,
                        50
                );

        MemoryDecayResult result =
                policy.calculate(
                        memory,
                        rememberedAt.plusDays(10)
                );

        assertThat(result.effectiveStrength())
                .isEqualTo(30);

        assertThat(result.compressionRequired())
                .isTrue();
    }

    @Test
    void 캐릭터의_기억력은_기본적으로_정상_상태다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Character character =
                new Character("Millie");

        var state =
                new com.example.projectmm.domain.character.CharacterState(
                        character,
                        70,
                        70,
                        now
                );

        assertThat(state.getMemoryAbility())
                .isEqualTo(100);
    }

    @Test
    void 캐릭터의_기억력은_0에서_100_사이로_제한된다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Character character =
                new Character("Millie");

        var state =
                new com.example.projectmm.domain.character.CharacterState(
                        character,
                        70,
                        70,
                        now
                );

        state.changeMemoryAbility(-30);

        assertThat(state.getMemoryAbility())
                .isZero();

        state.changeMemoryAbility(150);

        assertThat(state.getMemoryAbility())
                .isEqualTo(100);

        state.changeMemoryAbility(45);

        assertThat(state.getMemoryAbility())
                .isEqualTo(45);
    }

    @Test
    void 상태_변화량이_캐릭터의_기억력에_누적_반영된다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        12, 0
                );

        Character character =
                new Character("Millie");

        var state =
                new com.example.projectmm.domain.character.CharacterState(
                        character,
                        70,
                        70,
                        80,
                        now
                );

        state.apply(
                new com.example.projectmm.domain.simulation.StateDelta(
                        0,
                        0,
                        -30
                )
        );

        assertThat(state.getMemoryAbility())
                .isEqualTo(50);

        state.apply(
                new com.example.projectmm.domain.simulation.StateDelta(
                        0,
                        0,
                        -80
                )
        );

        assertThat(state.getMemoryAbility())
                .isZero();

        state.apply(
                new com.example.projectmm.domain.simulation.StateDelta(
                        0,
                        0,
                        150
                )
        );

        assertThat(state.getMemoryAbility())
                .isEqualTo(100);
    }

    @Test
    void narrativeAnchor가_없으면_기존_감쇠_정책을_그대로_사용한다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        6,
                        70,
                        0
                );

        MemoryDecayResult result =
                policy.calculate(
                        memory,
                        rememberedAt.plusDays(30)
                );

        assertThat(result.effectiveStrength())
                .isEqualTo(10);
    }

    @Test
    void narrativeAnchor는_기억_강도의_최저선을_보장한다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        6,
                        70,
                        5
                );

        MemoryDecayResult result =
                policy.calculate(
                        memory,
                        rememberedAt.plusDays(30)
                );

        assertThat(result.effectiveStrength())
                .isEqualTo(50);
    }

    @Test
    void narrativeAnchor가_높아도_자연감쇠된_기억은_압축할_수_있다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        9,
                        100,
                        10
                );

        MemoryDecayResult result =
                policy.calculate(
                        memory,
                        rememberedAt.plusDays(365)
                );

        assertThat(result.decayedStrength())
                .isZero();

        assertThat(result.effectiveStrength())
                .isEqualTo(100);

        assertThat(result.compressionRequired())
                .isTrue();
    }

    @Test
    void narrativeAnchor는_현재_기억_강도를_인위적으로_증가시키지_않는다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        5,
                        40,
                        8
                );

        MemoryDecayResult result =
                policy.calculate(
                        memory,
                        rememberedAt
                );

        assertThat(result.effectiveStrength())
                .isEqualTo(40);
    }

    private Memory createMemory(
            LocalDateTime rememberedAt,
            int importance,
            int strength
    ) {
        Character character =
                new Character("Millie");

        Scene scene =
                new Scene(
                        rememberedAt,
                        "기억 테스트",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        rememberedAt,
                        "INTERACTION",
                        "기억할 사건",
                        importance
                );

        return new Memory(
                character,
                event,
                event.getSummary(),
                importance,
                strength,
                rememberedAt
        );
    }

    

    private Memory createMemory(
            LocalDateTime rememberedAt,
            int importance,
            int strength,
            int narrativeAnchor
    ) {
        Character character =
                new Character("Millie");

        Scene scene =
                new Scene(
                        rememberedAt,
                        "기억 테스트",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        rememberedAt,
                        "INTERACTION",
                        "기억할 사건",
                        importance
                );

        return new Memory(
                character,
                event,
                event.getSummary(),
                importance,
                strength,
                narrativeAnchor,
                rememberedAt
        );
    }
}