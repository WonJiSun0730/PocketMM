package com.example.projectmm.application.ai;

import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiMemoryContext;
import com.example.projectmm.domain.ai.AiRelationshipContext;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import com.example.projectmm.domain.simulation.EventCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiContextBuilder {
    private final MemoryRecallService memoryRecallService;
    private final RelationshipRepository relationshipRepository;

    public AiContext build(
            CharacterState state,
            EventCandidate candidate
    ) {
        List<EffectiveMemory> recalledMemories =
                memoryRecallService.recall(
                        state.getCharacter().getId(),
                        state.getSimulatedAt(),
                        5
                );

        List<AiMemoryContext> memoryContexts =
                recalledMemories.stream()
                        .map(memory ->
                                new AiMemoryContext(
                                        memory.summary(),
                                        memory.importance(),
                                        memory.effectiveStrength(),
                                        memory.rememberedAt()
                                )
                        )
                        .toList();

        List<Relationship> relationships =
                relationshipRepository.findByFromCharacterId(
                        state.getCharacter().getId()
                );

        List<AiRelationshipContext> relationshipContexts =
                relationships.stream()
                        .map(relationship ->
                                new AiRelationshipContext(
                                        relationship
                                                .getToCharacter()
                                                .getId(),
                                        relationship
                                                .getToCharacter()
                                                .getName(),
                                        relationship.getAffinity(),
                                        relationship.getTrust(),
                                        relationship.getTension()
                                )
                        )
                        .toList();

        return new AiContext(
                state.getCharacter().getId(),
                state.getCharacter().getName(),
                state.getEnergy(),
                state.getMood(),
                state.getSimulatedAt(),
                candidate.eventType(),
                candidate.summary(),
                memoryContexts,
                relationshipContexts
        );
    }
}