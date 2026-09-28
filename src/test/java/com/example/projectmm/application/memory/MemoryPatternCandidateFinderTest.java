package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemoryPatternCandidateFinderTest {

    private final MemoryRepository memoryRepository =
            mock(MemoryRepository.class);

    private final MemoryPatternCandidateFinder finder =
            new MemoryPatternCandidateFinder(
                    memoryRepository
            );

    @Test
    void 저장되지_않은_캐릭터의_기억은_후보를_조회하지_않는다() {

        Character character =
                new Character("Millie");

        Memory memory =
                mock(Memory.class);

        when(memory.getCharacter())
                .thenReturn(character);

        List<Memory> result =
                finder.findRelated(memory);

        assertThat(result)
                .isEmpty();

        verifyNoInteractions(memoryRepository);
    }

    @Test
    void 현재_기억과_문맥이_겹치는_기억만_패턴_후보로_남긴다() {

        Character character =
                mock(Character.class);

        when(character.getId())
                .thenReturn(1L);

        Memory current =
                mock(Memory.class);

        Memory related =
                mock(Memory.class);

        Memory unrelated =
                mock(Memory.class);

        when(current.getCharacter())
                .thenReturn(character);

        when(current.getSummary())
                .thenReturn(
                        "마르바스가 위험한 상황에서 밀리를 도왔다."
                );

        when(related.getSummary())
                .thenReturn(
                        "마르바스는 전투에서 위험해진 밀리를 도왔다."
                );

        when(unrelated.getSummary())
                .thenReturn(
                        "밀리는 아침으로 토스트를 먹었다."
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                1L
                        )
        ).thenReturn(
                List.of(
                        current,
                        related,
                        unrelated
                )
        );

        List<Memory> result =
                finder.findRelated(current);

        assertThat(result)
                .containsExactly(related);

        verify(memoryRepository)
                .findByCharacterIdOrderByRememberedAtDesc(
                        1L
                );
    }

    @Test
    void 인물명만_겹치는_기억은_패턴_후보가_아니다() {

        Character character =
                mock(Character.class);

        when(character.getId())
                .thenReturn(1L);

        Memory current =
                mock(Memory.class);

        Memory sameCharacterNameOnly =
                mock(Memory.class);

        when(current.getCharacter())
                .thenReturn(character);

        when(current.getSummary())
                .thenReturn(
                        "마르바스가 위험한 상황에서 밀리를 도왔다."
                );

        when(sameCharacterNameOnly.getSummary())
                .thenReturn(
                        "밀리는 아침으로 토스트를 먹었다."
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                1L
                        )
        ).thenReturn(
                List.of(
                        current,
                        sameCharacterNameOnly
                )
        );

        List<Memory> result =
                finder.findRelated(current);

        assertThat(result)
                .isEmpty();
    }
}