package com.example.projectmm.application.memory;

import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDecision;
import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryCreationService {

    private final MemoryPolicy memoryPolicy;
    private final MemoryRepository memoryRepository;
    private final EventParticipantRepository eventParticipantRepository;

    public List<Memory> createFrom(
            Event event
    ) {
        MemoryDecision decision =
                memoryPolicy.evaluate(event);

        if (!decision.memorable()) {
            return List.of();
        }

        List<EventParticipant> participants =
                eventParticipantRepository.findByEventId(
                        event.getId()
                );

        List<Memory> memories =
                new ArrayList<>();

        for (EventParticipant participant : participants) {

            Memory memory =
                    new Memory(
                            participant.getCharacter(),
                            event,
                            event.getSummary(),
                            event.getImportance(),
                            decision.strength(),
                            event.getOccurredAt()
                    );

            memories.add(
                    memoryRepository.save(memory)
            );
        }

        return memories;
    }
}