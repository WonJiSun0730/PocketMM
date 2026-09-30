package com.example.projectmm.application.memory;

import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDecision;
import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class MemoryCreationService {

    private final MemoryPolicy memoryPolicy;
    private final MemoryRepository memoryRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final MemoryDetailService memoryDetailService;

    @Autowired
    public MemoryCreationService(
            MemoryPolicy memoryPolicy,
            MemoryRepository memoryRepository,
            EventParticipantRepository eventParticipantRepository,
            MemoryDetailService memoryDetailService
    ) {
        this.memoryPolicy = memoryPolicy;
        this.memoryRepository = memoryRepository;
        this.eventParticipantRepository = eventParticipantRepository;
        this.memoryDetailService = memoryDetailService;
    }

    public MemoryCreationService(
            MemoryPolicy memoryPolicy,
            MemoryRepository memoryRepository,
            EventParticipantRepository eventParticipantRepository
    ) {
        this(
                memoryPolicy,
                memoryRepository,
                eventParticipantRepository,
                null
        );
    }

    public List<Memory> createFrom(
            Event event
    ) {
        return createFrom(
                event,
                List.of()
        );
    }

    public List<Memory> createFrom(
            Event event,
            List<MemoryDetailCandidate> memoryDetails
    ) {
        return createFrom(event, memoryDetails, null);
    }

    public List<Memory> createFrom(Event event, List<MemoryDetailCandidate> memoryDetails,
                                  com.example.projectmm.domain.chat.Conversation room) {
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
                    room != null ? Memory.fromConversationEvent(participant.getCharacter(), room, event, decision.strength()) : new Memory(
                            participant.getCharacter(),
                            event,
                            event.getSummary(),
                            event.getImportance(),
                            decision.strength(),
                            event.getNarrativeAnchor(),
                            event.getOccurredAt()
                    );

            Memory persisted =
                    memoryRepository.save(memory);

            if (memoryDetailService != null) {
                memoryDetailService.saveDetails(
                        persisted,
                        memoryDetails
                );
            }

            memories.add(persisted);
        }

        return memories;
    }
}
