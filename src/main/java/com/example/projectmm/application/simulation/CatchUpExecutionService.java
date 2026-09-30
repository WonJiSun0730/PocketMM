package com.example.projectmm.application.simulation;

import com.example.projectmm.application.memory.MemoryAccessService;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.simulation.CatchUpExecutionResult;
import com.example.projectmm.domain.simulation.CatchUpSegment;
import com.example.projectmm.domain.simulation.EventCandidate;
import com.example.projectmm.domain.chat.Conversation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatchUpExecutionService {

    private final CatchUpOrchestrator catchUpOrchestrator;
    private final CatchUpSceneResolver catchUpSceneResolver;
    private final ResolvedEventPersistenceService persistenceService;
    private final MemoryAccessService memoryAccessService;

    @Transactional
    public List<Event> execute(
            CharacterState state,
            LocalDateTime currentTime
    ) {
        CatchUpExecutionResult result =
                catchUpOrchestrator.execute(
                        state,
                        currentTime
                );

        return persistResolved(state, result, null);
    }

    @Transactional
    public List<Event> execute(
            CharacterState state,
            CatchUpSegment segment,
            List<EventCandidate> prepared,
            Conversation room
    ) {
        return persist(
                state,
                resolve(state, segment, prepared, room),
                room
        );
    }

    public CatchUpExecutionResult resolve(
            CharacterState state,
            CatchUpSegment segment,
            List<EventCandidate> prepared,
            Conversation room
    ) {
        return resolve(
                state,
                segment,
                prepared,
                room,
                List.of()
        );
    }

    public CatchUpExecutionResult resolve(
            CharacterState state,
            CatchUpSegment segment,
            List<EventCandidate> prepared,
            Conversation room,
            List<String> transientNarrative
    ) {
        if (!state.getCharacter().getId().equals(room.getCharacter().getId())) {
            throw new IllegalArgumentException(
                    "catch-up character must match conversation"
            );
        }

        return catchUpOrchestrator.execute(
                state,
                segment,
                prepared,
                room,
                transientNarrative
        );
    }

    @Transactional
    public List<Event> persist(
            CharacterState state,
            CatchUpExecutionResult result,
            Conversation room
    ) {
        if (!state.getCharacter().getId().equals(room.getCharacter().getId())) {
            throw new IllegalArgumentException(
                    "catch-up character must match conversation"
            );
        }

        return persistResolved(
                state,
                result,
                room
        );
    }

    private List<Event> persistResolved(
            CharacterState state,
            CatchUpExecutionResult result,
            Conversation room
    ) {

        if (result.resolvedEvents().isEmpty()) {
            return List.of();
        }

        // 관계 변화와 같은 트랜잭션에서 기존 기억에 적용한다. AI가 대상 ID를 선택할 수 없다.
        for (var event : result.resolvedEvents()) {
            var change = event.memoryAccessChange();
            if (change == null) continue;
            var level = MemoryAccessLevel.valueOf(change.accessLevel());
            if (room != null) {
                memoryAccessService.changeAccessForConversation(state.getCharacter().getId(), room.getId(),
                        "ALL".equals(change.scope()) ? null : LocalDateTime.parse(change.since()), level, event.occurredAt());
            } else if ("ALL".equals(change.scope())) {
                memoryAccessService.changeAccessForCharacter(state.getCharacter().getId(), level, event.occurredAt());
            } else {
                memoryAccessService.changeAccessForCharacterSince(state.getCharacter().getId(),
                        LocalDateTime.parse(change.since()), level, event.occurredAt());
            }
        }

        Scene scene =
                room == null ? catchUpSceneResolver.resolveOrCreate(
                        state.getCharacter(),
                        result.catchUp().from()
                ) : catchUpSceneResolver.createForConversation(room, state.getCharacter(), result.catchUp().from());

        // Relationship deltas are applied exactly once by the persistence service.
        List<Event> savedEvents =
                room != null
                        ? persistenceService.saveAll(
                                scene,
                                state.getCharacter(),
                                result.resolvedEvents(),
                                room
                        )
                        : persistenceService.saveAll(
                                scene,
                                state.getCharacter(),
                                result.resolvedEvents()
                        );

        if (room != null && scene.isActive()) {
            scene.end(result.catchUp().to());
        }

        return savedEvents;
    }
}
