package com.example.projectmm.application.simulation;

import com.example.projectmm.application.relationship.RelationshipChangeService;
import com.example.projectmm.application.memory.MemoryAccessService;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.simulation.CatchUpExecutionResult;
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
    private final RelationshipChangeService relationshipChangeService;
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

        result.resolvedEvents().forEach(
                resolvedEvent ->
                        relationshipChangeService.apply(
                                state.getCharacter().getId(),
                                resolvedEvent.relationshipTargetCharacterId(),
                                resolvedEvent.relationshipDelta()
                        )
        );

        if (result.resolvedEvents().isEmpty()) {
            return List.of();
        }

        // 관계 변화와 같은 트랜잭션에서 기존 기억에 적용한다. AI가 대상 ID를 선택할 수 없다.
        for (var event : result.resolvedEvents()) {
            var change = event.memoryAccessChange();
            if (change == null) continue;
            var level = MemoryAccessLevel.valueOf(change.accessLevel());
            if ("ALL".equals(change.scope())) {
                memoryAccessService.changeAccessForCharacter(state.getCharacter().getId(), level, event.occurredAt());
            } else {
                memoryAccessService.changeAccessForCharacterSince(state.getCharacter().getId(),
                        LocalDateTime.parse(change.since()), level, event.occurredAt());
            }
        }

        Scene scene =
                catchUpSceneResolver.resolveOrCreate(
                        state.getCharacter(),
                        result.catchUp().from()
                );

        return persistenceService.saveAll(
                scene,
                state.getCharacter(),
                result.resolvedEvents()
        );
    }
}
