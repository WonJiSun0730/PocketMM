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
}