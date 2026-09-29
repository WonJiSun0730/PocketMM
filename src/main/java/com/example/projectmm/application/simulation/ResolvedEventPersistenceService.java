package com.example.projectmm.application.simulation;

import com.example.projectmm.application.memory.MemoryCreationService;
import com.example.projectmm.application.relationship.RelationshipChangeService;
import com.example.projectmm.application.relationship.RelationshipFactService;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.event.EventRepository;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResolvedEventPersistenceService {

    private final EventRepository eventRepository;
    private final ResolvedEventMapper resolvedEventMapper;
    private final EventParticipantRepository eventParticipantRepository;
    private final MemoryCreationService memoryCreationService;
    private final RelationshipChangeService relationshipChangeService;
    private final RelationshipFactService relationshipFactService;
    private final CharacterRepository characterRepository;

    public List<Event> saveAll(
            Scene scene,
            Character character,
            List<ResolvedEvent> resolvedEvents
    ) {
        List<Event> savedEvents =
                new ArrayList<>();

        for (ResolvedEvent resolvedEvent : resolvedEvents) {

            Event event =
                    resolvedEventMapper.toEntity(
                            scene,
                            resolvedEvent
                    );

            Event savedEvent =
                    eventRepository.save(event);

            EventParticipant participant =
                    new EventParticipant(
                            savedEvent,
                            character,
                            "ACTOR"
                    );

            eventParticipantRepository.save(
                    participant
            );

            memoryCreationService.createFrom(
                    savedEvent,
                    resolvedEvent.memoryDetails()
            );

            relationshipChangeService.apply(
                    character.getId(),
                    resolvedEvent.relationshipTargetCharacterId(),
                    resolvedEvent.relationshipDelta()
            );

            var relationshipFactChange =
                    resolvedEvent.relationshipFactChange();

            if (relationshipFactChange != null
                    && relationshipFactChange.hasChange()) {

                Character targetCharacter =
                        characterRepository
                                .findById(
                                        relationshipFactChange.targetCharacterId()
                                )
                                .orElseThrow(
                                        () -> new IllegalStateException(
                                                "Relationship fact target character not found: "
                                                        + relationshipFactChange.targetCharacterId()
                                        )
                                );

                relationshipFactService.establish(
                        character,
                        targetCharacter,
                        relationshipFactChange.relation(),
                        relationshipFactChange.value(),
                        savedEvent,
                        resolvedEvent.occurredAt()
                );
            }

            savedEvents.add(savedEvent);
        }

        return savedEvents;
    }
}