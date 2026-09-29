package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.*;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemoryCompressionServiceTest {

    private final MemoryDecayPolicy memoryDecayPolicy =
            new MemoryDecayPolicy();

    private final MemoryCompressor memoryCompressor =
            mock(MemoryCompressor.class);

    private final MemoryPatternCandidateFinder candidateFinder =
            mock(MemoryPatternCandidateFinder.class);

    private final MemoryCompressionPersistenceService persistence = mock(MemoryCompressionPersistenceService.class);

    @org.junit.jupiter.api.BeforeEach
    void allowPersistence() {
        when(persistence.apply(any(), any(), any(), any())).thenReturn(true);
    }

    private final MemoryCompressionService service =
            new MemoryCompressionService(
                    memoryDecayPolicy,
                    memoryCompressor,
                    candidateFinder,
                    persistence, mock(MemoryAccessStateRepository.class),
                    mock(MemoryProvenanceAccessPolicy.class)
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

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                )
        ).thenReturn(new MemoryCompressorOutput("과거에 중요한 대화를 나눈 기억.", List.of()));

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(10)
                );

        assertThat(result.compressed())
                .isTrue();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SUMMARY
                );

        assertThat(memory.getSummary())
                .isEqualTo(
                        "과거에 중요한 대화를 나눈 기억."
                );

        verify(memoryCompressor)
                .compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                );
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

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                )
        ).thenReturn(new MemoryCompressorOutput("관계를 정의하는 중요한 사건.", List.of()));

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(365)
                );

        assertThat(result.compressed())
                .isTrue();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SUMMARY
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
                .compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                );
    }

    @Test
    void 기억은_상세에서_요약_패턴_스키마_순서로_점진적으로_압축된다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        5,
                        30
                );

        Memory relatedMemory1 =
                mock(Memory.class);

        Memory relatedMemory2 =
                mock(Memory.class);

        when(
                candidateFinder.findRelated(memory)
        ).thenReturn(
                List.of(
                        relatedMemory1,
                        relatedMemory2
                )
        );

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                )
        ).thenReturn(new MemoryCompressorOutput("함께 힘든 일을 겪었다.", List.of()));

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.PATTERN,
                        List.of(
                                relatedMemory1,
                                relatedMemory2
                        )
                )
        ).thenReturn(new MemoryCompressorOutput("힘든 상황에서 서로 돕는 일이 반복된다.", List.of(2L)));

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.SCHEMA,
                        List.of(
                                relatedMemory1,
                                relatedMemory2
                        )
                )
        ).thenReturn(new MemoryCompressorOutput("의지할 수 있는 사람.", List.of(2L, 3L)));

        service.compressIfNeeded(
                memory,
                rememberedAt.plusDays(30)
        );

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SUMMARY
                );

        service.compressIfNeeded(
                memory,
                rememberedAt.plusDays(60)
        );

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.PATTERN
                );

        service.compressIfNeeded(
                memory,
                rememberedAt.plusDays(90)
        );

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SCHEMA
                );

        assertThat(memory.getSummary())
                .isEqualTo(
                        "의지할 수 있는 사람."
                );

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(120)
                );

        assertThat(result.compressed())
                .isFalse();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SCHEMA
                );

        verify(memoryCompressor)
                .compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                );

        verify(memoryCompressor)
                .compress(
                        memory,
                        MemoryDetailLevel.PATTERN,
                        List.of(
                                relatedMemory1,
                                relatedMemory2
                        )
                );

        verify(memoryCompressor)
                .compress(
                        memory,
                        MemoryDetailLevel.SCHEMA,
                        List.of(
                                relatedMemory1,
                                relatedMemory2
                        )
                );

        verify(
                candidateFinder,
                times(2)
        ).findRelated(memory);
    }

    @Test
    void 관련_기억이_없으면_요약에서_패턴으로_승격하지_않는다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        5,
                        30
                );

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                )
        ).thenReturn(new MemoryCompressorOutput("함께 힘든 일을 겪었다.", List.of()));

        service.compressIfNeeded(
                memory,
                rememberedAt.plusDays(30)
        );

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SUMMARY
                );

        when(
                candidateFinder.findRelated(memory)
        ).thenReturn(
                List.of()
        );

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(60)
                );

        assertThat(result.compressed())
                .isFalse();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.SUMMARY
                );

        assertThat(memory.getSummary())
                .isEqualTo(
                        "함께 힘든 일을 겪었다."
                );

        verify(memoryCompressor, never())
                .compress(
                        memory,
                        MemoryDetailLevel.PATTERN,
                        List.of()
                );
    }

    @Test
    void 관련_근거가_부족하면_패턴에서_스키마로_승격하지_않는다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 1,
                        12, 0
                );

        Memory memory =
                createMemory(
                        rememberedAt,
                        5,
                        30
                );

        Memory relatedMemory =
                mock(Memory.class);

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.SUMMARY,
                        List.of()
                )
        ).thenReturn(new MemoryCompressorOutput("함께 힘든 일을 겪었다.", List.of()));

        when(
                candidateFinder.findRelated(memory)
        ).thenReturn(
                List.of(relatedMemory)
        );

        when(
                memoryCompressor.compress(
                        memory,
                        MemoryDetailLevel.PATTERN,
                        List.of(relatedMemory)
                )
        ).thenReturn(new MemoryCompressorOutput("힘든 상황에서 서로 돕는 일이 반복된다.", List.of(2L)));

        service.compressIfNeeded(
                memory,
                rememberedAt.plusDays(30)
        );

        service.compressIfNeeded(
                memory,
                rememberedAt.plusDays(60)
        );

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.PATTERN
                );

        MemoryCompressionResult result =
                service.compressIfNeeded(
                        memory,
                        rememberedAt.plusDays(90)
                );

        assertThat(result.compressed())
                .isFalse();

        assertThat(memory.getDetailLevel())
                .isEqualTo(
                        MemoryDetailLevel.PATTERN
                );

        assertThat(memory.getSummary())
                .isEqualTo(
                        "힘든 상황에서 서로 돕는 일이 반복된다."
                );

        verify(memoryCompressor, never())
                .compress(
                        memory,
                        MemoryDetailLevel.SCHEMA,
                        List.of(relatedMemory)
                );
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
