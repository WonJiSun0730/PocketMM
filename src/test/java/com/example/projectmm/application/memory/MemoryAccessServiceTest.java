package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryAccessState;
import com.example.projectmm.domain.memory.MemoryAccessStateRepository;
import com.example.projectmm.domain.memory.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryAccessServiceTest {

    private MemoryRepository memoryRepository;
    private MemoryAccessStateRepository memoryAccessStateRepository;
    private MemoryAccessService memoryAccessService;
    private MemoryLifecycleService memoryLifecycleService;

    @BeforeEach
    void setUp() {
        memoryRepository =
                mock(MemoryRepository.class);

        memoryAccessStateRepository =
                mock(MemoryAccessStateRepository.class);

        memoryLifecycleService =
                mock(MemoryLifecycleService.class);

        memoryAccessService =
                new MemoryAccessService(
                        memoryRepository,
                        memoryAccessStateRepository,
                        memoryLifecycleService
                );
    }

    @Test
    void 접근_상태가_없으면_새로운_상태를_생성한다() {

        Long memoryId = 1L;

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        21, 0
                );

        Memory memory =
                createMemory(now);

        when(
                memoryRepository.findById(memoryId)
        ).thenReturn(
                Optional.of(memory)
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(memoryId)
        ).thenReturn(
                Optional.empty()
        );

        when(
                memoryAccessStateRepository.save(
                        any(MemoryAccessState.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        MemoryAccessState result =
                memoryAccessService.changeAccess(
                        memoryId,
                        MemoryAccessLevel.IMPLICIT,
                        now
                );

        assertThat(result.getMemory())
                .isSameAs(memory);

        assertThat(result.getAccessLevel())
                .isEqualTo(
                        MemoryAccessLevel.IMPLICIT
                );

        assertThat(result.getUpdatedAt())
                .isEqualTo(now);

        verify(
                memoryAccessStateRepository
        ).save(
                result
        );
    }

    @Test
    void 기존_접근_상태를_명시적_기억으로_회복한다() {

        Long memoryId = 2L;

        LocalDateTime before =
                LocalDateTime.of(
                        2026, 9, 27,
                        21, 0
                );

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        21, 0
                );

        Memory memory =
                createMemory(before);

        MemoryAccessState existingState =
                new MemoryAccessState(
                        memory,
                        MemoryAccessLevel.IMPLICIT,
                        before
                );

        when(
                memoryRepository.findById(memoryId)
        ).thenReturn(
                Optional.of(memory)
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(memoryId)
        ).thenReturn(
                Optional.of(existingState)
        );

        when(
                memoryAccessStateRepository.save(
                        existingState
                )
        ).thenReturn(
                existingState
        );

        MemoryAccessState result =
                memoryAccessService.changeAccess(
                        memoryId,
                        MemoryAccessLevel.EXPLICIT,
                        now
                );

        assertThat(result)
                .isSameAs(existingState);

        assertThat(result.getAccessLevel())
                .isEqualTo(
                        MemoryAccessLevel.EXPLICIT
                );

        assertThat(result.getUpdatedAt())
                .isEqualTo(now);

        verify(memoryLifecycleService)
                .markAccessed(
                        List.of(memoryId),
                        now
                );
    }

    @Test
    void 존재하지_않는_기억의_접근_상태는_변경할_수_없다() {

        Long memoryId = 999L;

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        21, 0
                );

        when(
                memoryRepository.findById(memoryId)
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> memoryAccessService.changeAccess(
                        memoryId,
                        MemoryAccessLevel.BLOCKED,
                        now
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "Memory not found: 999"
                );

        verify(
                memoryAccessStateRepository,
                never()
        ).save(
                any()
        );
    }

    @Test
    void 기억을_삭제하지_않고_접근만_차단한다() {

        Long memoryId = 3L;

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        21, 0
                );

        Memory memory =
                createMemory(now);

        when(
                memoryRepository.findById(memoryId)
        ).thenReturn(
                Optional.of(memory)
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(memoryId)
        ).thenReturn(
                Optional.empty()
        );

        when(
                memoryAccessStateRepository.save(
                        any(MemoryAccessState.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        MemoryAccessState result =
                memoryAccessService.changeAccess(
                        memoryId,
                        MemoryAccessLevel.BLOCKED,
                        now
                );

        assertThat(result.getMemory())
                .isSameAs(memory);

        assertThat(result.getAccessLevel())
                .isEqualTo(
                        MemoryAccessLevel.BLOCKED
                );

        verify(
                memoryRepository,
                never()
        ).delete(
                any(Memory.class)
        );

        verify(
                memoryRepository,
                never()
        ).deleteById(
                anyLong()
        );
    }

    @Test
    void 캐릭터의_모든_기억_접근을_차단한다() {

        Long characterId = 1L;

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        21, 30
                );

        Memory firstMemory =
                createMemory(
                        now.minusDays(3)
                );

        Memory secondMemory =
                createMemory(
                        now.minusDays(2)
                );

        Memory thirdMemory =
                createMemory(
                        now.minusDays(1)
                );

        ReflectionTestUtils.setField(
                firstMemory,
                "id",
                101L
        );

        ReflectionTestUtils.setField(
                secondMemory,
                "id",
                102L
        );

        ReflectionTestUtils.setField(
                thirdMemory,
                "id",
                103L
        );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        )
        ).thenReturn(
                List.of(
                        firstMemory,
                        secondMemory,
                        thirdMemory
                )
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(anyLong())
        ).thenReturn(
                Optional.empty()
        );

        when(
                memoryAccessStateRepository.save(
                        any(MemoryAccessState.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        List<MemoryAccessState> results =
                memoryAccessService
                        .changeAccessForCharacter(
                                characterId,
                                MemoryAccessLevel.BLOCKED,
                                now
                        );

        assertThat(results)
                .hasSize(3)
                .allSatisfy(
                        state -> {
                            assertThat(
                                    state.getAccessLevel()
                            ).isEqualTo(
                                    MemoryAccessLevel.BLOCKED
                            );

                            assertThat(
                                    state.getUpdatedAt()
                            ).isEqualTo(now);
                        }
                );

        verify(
                memoryAccessStateRepository,
                times(3)
        ).save(
                any(MemoryAccessState.class)
        );
    }

    @Test
    void 캐릭터의_차단된_기억을_모두_명시적_기억으로_회복한다() {

        Long characterId = 1L;

        LocalDateTime before =
                LocalDateTime.of(
                        2026, 9, 27,
                        21, 30
                );

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        21, 30
                );

        Memory firstMemory =
                createMemory(before);

        Memory secondMemory =
                createMemory(before);

        ReflectionTestUtils.setField(
                firstMemory,
                "id",
                201L
        );

        ReflectionTestUtils.setField(
                secondMemory,
                "id",
                202L
        );

        MemoryAccessState firstState =
                new MemoryAccessState(
                        firstMemory,
                        MemoryAccessLevel.BLOCKED,
                        before
                );

        MemoryAccessState secondState =
                new MemoryAccessState(
                        secondMemory,
                        MemoryAccessLevel.BLOCKED,
                        before
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        )
        ).thenReturn(
                List.of(
                        firstMemory,
                        secondMemory
                )
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(201L)
        ).thenReturn(
                Optional.of(firstState)
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(202L)
        ).thenReturn(
                Optional.of(secondState)
        );

        when(
                memoryAccessStateRepository.save(
                        any(MemoryAccessState.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        List<MemoryAccessState> results =
                memoryAccessService
                        .changeAccessForCharacter(
                                characterId,
                                MemoryAccessLevel.EXPLICIT,
                                now
                        );

        assertThat(results)
                .containsExactly(
                        firstState,
                        secondState
                );

        assertThat(firstState.getAccessLevel())
                .isEqualTo(
                        MemoryAccessLevel.EXPLICIT
                );

        assertThat(secondState.getAccessLevel())
                .isEqualTo(
                        MemoryAccessLevel.EXPLICIT
                );

        assertThat(firstState.getUpdatedAt())
                .isEqualTo(now);

        assertThat(secondState.getUpdatedAt())
                .isEqualTo(now);

        verify(memoryLifecycleService)
                .markAccessed(
                        List.of(
                                201L,
                                202L
                        ),
                        now
                );
    }

    @Test
    void 특정_시점_이후의_기억만_접근_상태를_변경한다() {

        Long characterId = 1L;

        LocalDateTime since =
                LocalDateTime.of(
                        2026, 6, 1,
                        0, 0
                );

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 28,
                        22, 0
                );

        Memory oldMemory =
                createMemory(
                        since.minusDays(1)
                );

        Memory boundaryMemory =
                createMemory(since);

        Memory recentMemory =
                createMemory(
                        since.plusDays(30)
                );

        ReflectionTestUtils.setField(
                oldMemory,
                "id",
                301L
        );

        ReflectionTestUtils.setField(
                boundaryMemory,
                "id",
                302L
        );

        ReflectionTestUtils.setField(
                recentMemory,
                "id",
                303L
        );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        )
        ).thenReturn(
                List.of(
                        recentMemory,
                        boundaryMemory,
                        oldMemory
                )
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(anyLong())
        ).thenReturn(
                Optional.empty()
        );

        when(
                memoryAccessStateRepository.save(
                        any(MemoryAccessState.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        List<MemoryAccessState> results =
                memoryAccessService
                        .changeAccessForCharacterSince(
                                characterId,
                                since,
                                MemoryAccessLevel.BLOCKED,
                                now
                        );

        assertThat(results)
                .hasSize(2);

        assertThat(results)
                .extracting(
                        state ->
                                state.getMemory().getId()
                )
                .containsExactly(
                        303L,
                        302L
                );

        assertThat(results)
                .allSatisfy(
                        state ->
                                assertThat(
                                        state.getAccessLevel()
                                ).isEqualTo(
                                        MemoryAccessLevel.BLOCKED
                                )
                );

        verify(
                memoryAccessStateRepository,
                never()
        ).findByMemoryId(301L);

        verify(
                memoryAccessStateRepository,
                times(2)
        ).save(
                any(MemoryAccessState.class)
        );
    }

    @Test
    void 특정_시점_이후의_차단된_기억만_명시적으로_회복한다() {

        Long characterId = 1L;

        LocalDateTime since =
                LocalDateTime.of(
                        2026, 6, 1,
                        0, 0
                );

        LocalDateTime before =
                LocalDateTime.of(
                        2026, 5, 1,
                        0, 0
                );

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        11, 30
                );

        Memory oldMemory =
                createMemory(before);

        Memory boundaryMemory =
                createMemory(since);

        Memory recentMemory =
                createMemory(
                        since.plusDays(30)
                );

        ReflectionTestUtils.setField(
                oldMemory,
                "id",
                401L
        );

        ReflectionTestUtils.setField(
                boundaryMemory,
                "id",
                402L
        );

        ReflectionTestUtils.setField(
                recentMemory,
                "id",
                403L
        );

        MemoryAccessState boundaryState =
                new MemoryAccessState(
                        boundaryMemory,
                        MemoryAccessLevel.BLOCKED,
                        before
                );

        MemoryAccessState recentState =
                new MemoryAccessState(
                        recentMemory,
                        MemoryAccessLevel.RESIDUAL,
                        before
                );

        when(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        )
        ).thenReturn(
                List.of(
                        recentMemory,
                        boundaryMemory,
                        oldMemory
                )
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(402L)
        ).thenReturn(
                Optional.of(boundaryState)
        );

        when(
                memoryAccessStateRepository
                        .findByMemoryId(403L)
        ).thenReturn(
                Optional.of(recentState)
        );

        when(
                memoryAccessStateRepository.save(
                        any(MemoryAccessState.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        List<MemoryAccessState> results =
                memoryAccessService
                        .changeAccessForCharacterSince(
                                characterId,
                                since,
                                MemoryAccessLevel.EXPLICIT,
                                now
                        );

        assertThat(results)
                .containsExactly(
                        recentState,
                        boundaryState
                );

        assertThat(results)
                .allSatisfy(
                        state ->
                                assertThat(
                                        state.getAccessLevel()
                                ).isEqualTo(
                                        MemoryAccessLevel.EXPLICIT
                                )
                );

        verify(
                memoryAccessStateRepository,
                never()
        ).findByMemoryId(401L);

        verify(memoryLifecycleService)
                .markAccessed(
                        List.of(
                                403L,
                                402L
                        ),
                        now
                );
    }

    private Memory createMemory(
            LocalDateTime rememberedAt
    ) {
        Character character =
                new Character("Millie");

        return new Memory(
                character,
                (Event) null,
                "마르바스와 있었던 중요한 사건",
                9,
                90,
                rememberedAt
        );
    }
}