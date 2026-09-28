package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryCompressionResult;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemoryCompressionServiceTest {

    private final MemoryDecayPolicy memoryDecayPolicy =
            new MemoryDecayPolicy();

    private final MemoryCompressor memoryCompressor =
            mock(MemoryCompressor.class);

    private final MemoryCompressionService service =
            new MemoryCompressionService(
                    memoryDecayPolicy,
                    memoryCompressor
            );

    @Test
    void 약해진_상세_기억은_압축한다() {

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

        when(memoryCompressor.compress(memory))
                .thenReturn(
                        "과거에 중요한 대화를 나눈 기억."
                );

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(10)
                );

        assertThat(result.compressed())
                .isTrue();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.COMPRESSED
                );

        assertThat(memory.getSummary())
                .isEqualTo(
                        "과거에 중요한 대화를 나눈 기억."
                );

        verify(memoryCompressor)
                .compress(memory);
    }

    @Test
    void 아직_강한_기억은_압축하지_않는다() {

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

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(10)
                );

        assertThat(result.compressed())
                .isFalse();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.DETAILED
                );

        verifyNoInteractions(memoryCompressor);
    }

    @Test
    void 이미_압축된_기억은_다시_압축하지_않는다() {

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

        memory.compress(
                "이미 압축된 기억."
        );

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(20)
                );

        assertThat(result.compressed())
                .isFalse();

        assertThat(memory.getSummary())
                .isEqualTo(
                        "이미 압축된 기억."
                );

        verifyNoInteractions(memoryCompressor);
    }

    @Test
    void narrativeAnchor가_높아도_자연감쇠되면_상세기억은_압축한다() {

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

        when(memoryCompressor.compress(memory))
                .thenReturn(
                        "관계를 정의하는 중요한 사건."
                );

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(365)
                );

        assertThat(result.compressed())
                .isTrue();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.COMPRESSED
                );

        assertThat(memory.getSummary())
                .isEqualTo(
                        "관계를 정의하는 중요한 사건."
                );

        MemoryDecayResult decay =
                memoryDecayPolicy.calculate(
                        memory,
                        rememberedAt.plusDays(365)
                );

        assertThat(decay.decayedStrength())
                .isZero();

        assertThat(decay.effectiveStrength())
                .isEqualTo(100);

        verify(memoryCompressor)
                .compress(memory);
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
                        "길고 자세한 원본 기억.",
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
                        "길고 자세한 원본 기억.",
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