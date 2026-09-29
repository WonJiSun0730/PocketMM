package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.memory.*;
import com.example.projectmm.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryCreationServiceTest {

    @Test
    void 중요한_이벤트는_참가자별_기억으로_생성된다() {

        MemoryRepository memoryRepository =
                mock(MemoryRepository.class);

        EventParticipantRepository eventParticipantRepository =
                mock(EventParticipantRepository.class);

        MemoryCreationService service =
                new MemoryCreationService(
                        new MemoryPolicy(),
                        memoryRepository,
                        eventParticipantRepository
                );

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        Scene scene =
                new Scene(
                        LocalDateTime.now(),
                        "중요한 대화",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        LocalDateTime.now(),
                        "INTERACTION",
                        "두 사람이 중요한 이야기를 나누었다.",
                        7
                );

        EventParticipant millieParticipant =
                new EventParticipant(
                        event,
                        millie,
                        "ACTOR"
                );

        EventParticipant marbasParticipant =
                new EventParticipant(
                        event,
                        marbas,
                        "PARTICIPANT"
                );

        when(
                eventParticipantRepository.findByEventId(
                        event.getId()
                )
        ).thenReturn(
                List.of(
                        millieParticipant,
                        marbasParticipant
                )
        );

        when(memoryRepository.save(any(Memory.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        List<Memory> memories =
                service.createFrom(event);

        assertThat(memories)
                .hasSize(2);

        assertThat(memories)
                .extracting(memory ->
                        memory.getCharacter().getName()
                )
                .containsExactlyInAnyOrder(
                        "Millie",
                        "Marbas"
                );

        assertThat(memories)
                .allSatisfy(memory -> {
                    assertThat(memory.getStrength())
                            .isEqualTo(70);

                    assertThat(memory.getSourceEvent())
                            .isSameAs(event);
                });

        verify(memoryRepository, times(2))
                .save(any(Memory.class));
    }

    @Test
    void 이벤트의_narrativeAnchor가_생성된_기억에_전달된다() {

        MemoryRepository memoryRepository =
                mock(MemoryRepository.class);

        EventParticipantRepository eventParticipantRepository =
                mock(EventParticipantRepository.class);

        MemoryCreationService service =
                new MemoryCreationService(
                        new MemoryPolicy(),
                        memoryRepository,
                        eventParticipantRepository
                );

        Character millie =
                new Character("Millie");

        LocalDateTime occurredAt =
                LocalDateTime.of(
                        2026, 9, 28,
                        22, 0
                );

        Scene scene =
                new Scene(
                        occurredAt,
                        "핵심 사건",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        occurredAt,
                        "RELATIONSHIP",
                        "두 사람의 관계를 바꾸는 중요한 사건이 발생했다.",
                        7,
                        8
                );

        EventParticipant participant =
                new EventParticipant(
                        event,
                        millie,
                        "ACTOR"
                );

        when(
                eventParticipantRepository.findByEventId(
                        event.getId()
                )
        ).thenReturn(
                List.of(participant)
        );

        when(memoryRepository.save(any(Memory.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        List<Memory> memories =
                service.createFrom(event);

        assertThat(memories)
                .hasSize(1);

        Memory memory =
                memories.getFirst();

        assertThat(memory.getNarrativeAnchor())
                .isEqualTo(8);

        assertThat(memory.getImportance())
                .isEqualTo(7);

        assertThat(memory.getStrength())
                .isEqualTo(70);

        assertThat(memory.getSourceEvent())
                .isSameAs(event);
    }

    @Test
    void 중요도가_낮으면_기억도_참가자조회도_하지_않는다() {

        MemoryRepository memoryRepository =
                mock(MemoryRepository.class);

        EventParticipantRepository eventParticipantRepository =
                mock(EventParticipantRepository.class);

        MemoryCreationService service =
                new MemoryCreationService(
                        new MemoryPolicy(),
                        memoryRepository,
                        eventParticipantRepository
                );

        Scene scene =
                new Scene(
                        LocalDateTime.now(),
                        "휴식",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        LocalDateTime.now(),
                        "REST",
                        "잠시 쉬었다.",
                        2
                );

        List<Memory> memories =
                service.createFrom(event);

        assertThat(memories)
                .isEmpty();

        verifyNoInteractions(
                eventParticipantRepository
        );

        verifyNoInteractions(
                memoryRepository
        );
    }

    @Test
    void 이벤트_memoryDetails가_생성된_기억의_protectedDetail로_저장된다() {

        MemoryRepository memoryRepository =
                mock(MemoryRepository.class);

        EventParticipantRepository eventParticipantRepository =
                mock(EventParticipantRepository.class);

        ProtectedMemoryDetailRepository protectedDetailRepository =
                mock(ProtectedMemoryDetailRepository.class);

        MemoryDetailService memoryDetailService =
                new MemoryDetailService(
                        protectedDetailRepository
                );

        MemoryCreationService service =
                new MemoryCreationService(
                        new MemoryPolicy(),
                        memoryRepository,
                        eventParticipantRepository,
                        memoryDetailService
                );

        Character millie =
                new Character("Millie");

        LocalDateTime occurredAt =
                LocalDateTime.of(
                        2026, 9, 29,
                        15, 0
                );

        Scene scene =
                new Scene(
                        occurredAt,
                        "관계 사건",
                        "DAILY"
                );

        Event event =
                new Event(
                        scene,
                        occurredAt,
                        "RELATIONSHIP_MILESTONE",
                        "마르바스와 붉은 반지를 교환하며 약속을 나눴다.",
                        8,
                        9
                );

        EventParticipant participant =
                new EventParticipant(
                        event,
                        millie,
                        "ACTOR"
                );

        when(
                eventParticipantRepository.findByEventId(
                        event.getId()
                )
        ).thenReturn(
                List.of(participant)
        );

        when(memoryRepository.save(any(Memory.class)))
                .thenAnswer(invocation -> {
                    Memory memory =
                            invocation.getArgument(0);

                    org.springframework.test.util.ReflectionTestUtils
                            .setField(
                                    memory,
                                    "id",
                                    1L
                            );

                    return memory;
                });

        when(
                protectedDetailRepository.save(
                        any(ProtectedMemoryDetail.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        List<Memory> memories =
                service.createFrom(
                        event,
                        List.of(
                                new MemoryDetailCandidate(
                                        "붉은 반지",
                                        MemoryDetailSalience.HIGH,
                                        false
                                ),
                                new MemoryDetailCandidate(
                                        "약속",
                                        MemoryDetailSalience.CRITICAL,
                                        true
                                ),
                                new MemoryDetailCandidate(
                                        "교환",
                                        MemoryDetailSalience.LOW,
                                        false
                                )
                        )
                );

        assertThat(memories)
                .hasSize(1);

        verify(
                protectedDetailRepository,
                times(2)
        ).save(
                any(ProtectedMemoryDetail.class)
        );

        verify(protectedDetailRepository)
                .save(
                        argThat(detail ->
                                detail.getDetailText()
                                        .equals("붉은 반지")
                                        && detail.getSalience()
                                        == MemoryDetailSalience.HIGH
                                        && detail.mustPreserve()
                        )
                );

        verify(protectedDetailRepository)
                .save(
                        argThat(detail ->
                                detail.getDetailText()
                                        .equals("약속")
                                        && detail.getSalience()
                                        == MemoryDetailSalience.CRITICAL
                                        && detail.mustPreserve()
                        )
                );

        verify(
                protectedDetailRepository,
                never()
        ).save(
                argThat(detail ->
                        detail.getDetailText()
                                .equals("교환")
                )
        );
    }
}