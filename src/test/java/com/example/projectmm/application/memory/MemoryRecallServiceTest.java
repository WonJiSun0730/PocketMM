package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.*;

class MemoryRecallServiceTest {

    private final MemoryRepository memoryRepository =
            mock(MemoryRepository.class);

    private final MemoryDecayPolicy memoryDecayPolicy =
            new MemoryDecayPolicy();

    private final MemoryCompressionService memoryCompressionService =
            mock(MemoryCompressionService.class);

    private final MemoryRecallService memoryRecallService =
            new MemoryRecallService(
                    memoryRepository,
                    memoryDecayPolicy,
                    memoryCompressionService
            );

    @Test
    void 현재_강도가_높은_기억부터_회상하고_소멸한_기억은_제외한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        Memory recentMemory =
                createMemory(
                        "최근의 평범한 기억",
                        5,
                        50,
                        now.minusDays(1)
                );

        Memory importantOldMemory =
                createMemory(
                        "오래됐지만 중요한 기억",
                        9,
                        90,
                        now.minusDays(20)
                );

        Memory fadedMemory =
                createMemory(
                        "거의 잊힌 오래된 기억",
                        5,
                        40,
                        now.minusDays(30)
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(1L)
        ).thenReturn(
                List.of(
                        recentMemory,
                        importantOldMemory,
                        fadedMemory
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recall(
                        1L,
                        now,
                        10
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .isEqualTo(
                        "오래됐지만 중요한 기억"
                );

        assertThat(result.get(0).effectiveStrength())
                .isEqualTo(70);

        assertThat(result.get(1).summary())
                .isEqualTo(
                        "최근의 평범한 기억"
                );

        assertThat(result.get(1).effectiveStrength())
                .isEqualTo(48);

        assertThat(result)
                .noneMatch(memory ->
                        memory.summary()
                                .equals("거의 잊힌 오래된 기억")
                );
    }

    @Test
    void 회상할_기억의_개수를_제한한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        Memory strongest =
                createMemory(
                        "가장 강한 기억",
                        9,
                        90,
                        now.minusDays(1)
                );

        Memory middle =
                createMemory(
                        "중간 기억",
                        7,
                        70,
                        now.minusDays(1)
                );

        Memory weakest =
                createMemory(
                        "약한 기억",
                        5,
                        50,
                        now.minusDays(1)
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(1L)
        ).thenReturn(
                List.of(
                        weakest,
                        strongest,
                        middle
                )
        );

        List<EffectiveMemory> result =
                memoryRecallService.recall(
                        1L,
                        now,
                        2
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result.get(0).summary())
                .isEqualTo("가장 강한 기억");

        assertThat(result.get(1).summary())
                .isEqualTo("중간 기억");
    }

    @Test
    void 희미하지만_남아있는_기억은_회상_과정에서_압축을_요청한다() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        Memory fadingMemory =
                createMemory(
                        "희미해지는 기억",
                        5,
                        50,
                        now.minusDays(10)
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(1L)
        ).thenReturn(
                List.of(fadingMemory)
        );

        memoryRecallService.recall(
                1L,
                now,
                10
        );

        verify(memoryCompressionService)
                .compressIfNeeded(
                        fadingMemory,
                        now
                );
    }

    private Memory createMemory(
            String summary,
            int importance,
            int strength,
            LocalDateTime rememberedAt
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
                        summary,
                        importance
                );

        return new Memory(
                character,
                event,
                summary,
                importance,
                strength,
                rememberedAt
        );
    }
}