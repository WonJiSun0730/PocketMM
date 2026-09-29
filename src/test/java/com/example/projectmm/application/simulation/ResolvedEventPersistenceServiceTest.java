package com.example.projectmm.application.simulation;

import com.example.projectmm.application.memory.MemoryCreationService;
import com.example.projectmm.application.relationship.RelationshipChangeService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.event.EventRepository;
import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.simulation.EventSource;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResolvedEventPersistenceServiceTest {

    @Test
    void ResolvedEvent_저장시_관계_변화량을_실제_관계에_적용한다() {

        EventRepository eventRepository =
                mock(EventRepository.class);

        EventParticipantRepository eventParticipantRepository =
                mock(EventParticipantRepository.class);

        MemoryCreationService memoryCreationService =
                mock(MemoryCreationService.class);

        RelationshipRepository relationshipRepository =
                mock(RelationshipRepository.class);

        Character actor =
                mock(Character.class);

        Character target =
                new Character("Marbas");

        Scene scene =
                mock(Scene.class);

        when(actor.getId())
                .thenReturn(1L);

        Relationship relationship =
                new Relationship(
                        actor,
                        target
                );

        when(
                relationshipRepository
                        .findByFromCharacterIdAndToCharacterId(
                                1L,
                                2L
                        )
        ).thenReturn(
                Optional.of(relationship)
        );

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        RelationshipChangeService relationshipChangeService =
                new RelationshipChangeService(
                        relationshipRepository
                );

        ResolvedEventPersistenceService service =
                new ResolvedEventPersistenceService(
                        eventRepository,
                        new ResolvedEventMapper(),
                        eventParticipantRepository,
                        memoryCreationService,
                        relationshipChangeService
                );

        ResolvedEvent resolvedEvent =
                new ResolvedEvent(
                        LocalDateTime.of(
                                2026,
                                9,
                                29,
                                20,
                                0
                        ),
                        "RELATIONSHIP_TEST",
                        "관계 변화 테스트",
                        5,
                        null,
                        2L,
                        new RelationshipDelta(
                                10,
                                7,
                                3
                        ),
                        EventSource.AI
                );

        service.saveAll(
                scene,
                actor,
                List.of(resolvedEvent)
        );

        assertThat(
                relationship.getAffinity()
        ).isEqualTo(10);

        assertThat(
                relationship.getTrust()
        ).isEqualTo(7);

        assertThat(
                relationship.getTension()
        ).isEqualTo(3);

        verify(
                relationshipRepository
        ).findByFromCharacterIdAndToCharacterId(
                1L,
                2L
        );
    }
}