package com.example.projectmm.application.ai;

import com.example.projectmm.application.memory.MemoryReactivationContextService;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.ai.AiContext;
import com.example.projectmm.domain.ai.AiMemoryContext;
import com.example.projectmm.domain.ai.AiRelationshipContext;
import com.example.projectmm.domain.ai.AiRelationshipFactContext;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.relationship.RelationshipFactRepository;
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
    private final MemoryReactivationContextService reactivationContextService;
    private final RelationshipFactRepository relationshipFactRepository;


    public AiContextBuilder(
            MemoryRecallService memoryRecallService,
            RelationshipRepository relationshipRepository
    ) {
        this(
                memoryRecallService,
                relationshipRepository,
                null,
                null
        );
    }

    public AiContextBuilder(
            MemoryRecallService memoryRecallService,
            RelationshipRepository relationshipRepository,
            MemoryReactivationContextService reactivationContextService
    ) {
        this(
                memoryRecallService,
                relationshipRepository,
                reactivationContextService,
                null
        );
    }

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
                        .map(this::toMemoryContext)
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

        List<AiRelationshipFactContext> relationshipFactContexts =
                relationshipFactRepository == null
                        ? List.of()
                        : relationshipFactRepository
                        .findByFromCharacterId(
                                state.getCharacter().getId()
                        )
                        .stream()
                        .map(fact ->
                                new AiRelationshipFactContext(
                                        fact.getToCharacter().getId(),
                                        fact.getToCharacter().getName(),
                                        fact.getRelation(),
                                        fact.getValue()
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
                relationshipContexts,
                relationshipFactContexts
        );
    }

    private AiMemoryContext toMemoryContext(
            EffectiveMemory memory
    ) {
        return switch (memory.tier()) {
            case HOT ->
                    new AiMemoryContext(
                            hotMemorySummary(memory),
                            memory.importance(),
                            memory.effectiveStrength(),
                            memory.rememberedAt()
                    );

            case WARM ->
                    new AiMemoryContext(
                            memorySummary(memory),
                            memory.importance(),
                            0,
                            memory.rememberedAt()
                    );

            case COLD ->
                    new AiMemoryContext(
                            memorySummary(memory),
                            0,
                            0,
                            memory.rememberedAt()
                    );
        };
    }

    private String hotMemorySummary(
            EffectiveMemory memory
    ) {
        String baseSummary =
                memorySummary(memory);

        if (memory.accessLevel()
                != MemoryAccessLevel.EXPLICIT
                || reactivationContextService == null) {
            return baseSummary;
        }

        List<String> evidenceSummaries =
                reactivationContextService
                        .accessibleEvidence(
                                memory.memoryId()
                        )
                        .stream()
                        .map(item -> item.getSummary())
                        .filter(summary ->
                                summary != null
                                        && !summary.isBlank()
                        )
                        .distinct()
                        .limit(3)
                        .toList();

        if (evidenceSummaries.isEmpty()) {
            return baseSummary;
        }

        return baseSummary
                + "\n관련 회상 근거: "
                + String.join(
                " / ",
                evidenceSummaries
        );
    }

    private String memorySummary(
            EffectiveMemory memory
    ) {
        return switch (memory.accessLevel()) {
            case EXPLICIT ->
                    memory.summary();

            case IMPLICIT ->
                    "구체적인 이유나 사건은 기억나지 않지만, "
                            + "이 대상이나 상황에 익숙함을 느낀다.";

            case RESIDUAL ->
                    "관련된 구체적인 기억은 떠오르지 않지만, "
                            + "이유를 특정할 수 없는 감정적 잔향이 남아 있다.";

            case BLOCKED ->
                    throw new IllegalStateException(
                            "BLOCKED memory must not reach AI context"
                    );
        };
    }

}