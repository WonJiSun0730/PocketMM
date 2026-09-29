package com.example.projectmm.application.simulation;

import com.example.projectmm.application.memory.MemoryCreationService;
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

            savedEvents.add(savedEvent);
        }

        return savedEvents;
    }
}