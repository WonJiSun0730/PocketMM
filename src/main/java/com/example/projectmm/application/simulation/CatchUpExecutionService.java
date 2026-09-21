package com.example.projectmm.application.simulation;

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

        if (result.resolvedEvents().isEmpty()) {
            return List.of();
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