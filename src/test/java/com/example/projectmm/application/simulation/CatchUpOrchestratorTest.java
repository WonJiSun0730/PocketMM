package com.example.projectmm.application.simulation;

import com.example.projectmm.application.ai.AiContextBuilder;
import com.example.projectmm.application.ai.AiDecisionValidator;
import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import com.example.projectmm.domain.simulation.CatchUpExecutionResult;
import com.example.projectmm.domain.simulation.EventSource;
import com.example.projectmm.domain.simulation.ResolvedEvent;
import com.example.projectmm.infrastructure.ai.MockAiGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CatchUpOrchestratorTest {

    private final CatchUpSimulationService simulationService =
            new CatchUpSimulationService();

    private final CatchUpSegmentService segmentService =
            new CatchUpSegmentService();

    private final CatchUpEventCandidateService candidateService =
            new CatchUpEventCandidateService();

    private final EventCandidateValidator validator =
            new EventCandidateValidator();

    private final MemoryRecallService memoryRecallService =
            mock(MemoryRecallService.class);

    private final RelationshipRepository relationshipRepository =
            mock(RelationshipRepository.class);

    private final AiContextBuilder aiContextBuilder =
            new AiContextBuilder(
                    memoryRecallService,
                    relationshipRepository
            );

    private final AiGateway aiGateway =
            new MockAiGateway();

    private final AiDecisionValidator aiDecisionValidator =
            new AiDecisionValidator();

    private final CatchUpOrchestrator orchestrator =
            new CatchUpOrchestrator(
                    simulationService,
                    segmentService,
                    candidateService,
                    validator,
                    aiContextBuilder,
                    aiGateway,
                    aiDecisionValidator
            );

    @Test
    void 캐릭터의_미접속_시간을_시뮬레이션한다() {

        LocalDateTime lastSimulatedAt =
                LocalDateTime.of(2026, 9, 20, 15, 30);

        LocalDateTime currentTime =
                LocalDateTime.of(2026, 9, 21, 10, 0);

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        30,
                        70,
                        lastSimulatedAt
                );

        CatchUpExecutionResult result =
                orchestrator.execute(
                        state,
                        currentTime
                );

        assertThat(result.catchUp().capped())
                .isFalse();

        assertThat(result.segments())
                .hasSize(4);

        assertThat(result.candidates())
                .hasSize(1);

        assertThat(result.candidates().getFirst().eventType())
                .isEqualTo("REST");

        assertThat(result.candidates().getFirst().requiresAi())
                .isFalse();

        assertThat(state.getEnergy())
                .isEqualTo(60);

        assertThat(state.getSimulatedAt())
                .isEqualTo(currentTime);

        assertThat(result.resolvedEvents())
                .hasSize(1);

        assertThat(result.resolvedEvents().getFirst().eventType())
                .isEqualTo("REST");

        assertThat(result.resolvedEvents().getFirst().source())
                .isEqualTo(EventSource.RULE);
    }

    @Test
    void AI가_필요한_후보를_AI로_처리하고_상태에_반영한다() {

        LocalDateTime last =
                LocalDateTime.of(2026, 9, 21, 12, 0);

        LocalDateTime now =
                LocalDateTime.of(2026, 9, 21, 13, 0);

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        80,
                        10,
                        last
                );

        CatchUpExecutionResult result =
                orchestrator.execute(state, now);

        assertThat(result.candidates())
                .hasSize(1);

        assertThat(result.candidates().getFirst().requiresAi())
                .isTrue();

        assertThat(state.getMood())
                .isEqualTo(15);

        assertThat(state.getSimulatedAt())
                .isEqualTo(now);

        assertThat(result.resolvedEvents())
                .hasSize(1);

        ResolvedEvent resolved =
                result.resolvedEvents().getFirst();

        assertThat(resolved.eventType())
                .isEqualTo("REFLECTION");

        assertThat(resolved.source())
                .isEqualTo(EventSource.AI);

        assertThat(resolved.stateDelta().moodChange())
                .isEqualTo(5);
    }

    @Test
    void AI가_결정한_기억력_변화가_캐릭터_상태에_반영된다() {

        AiGateway memoryChangingGateway =
                context ->
                        new com.example.projectmm.domain.ai.AiDecision(
                                "REFLECTION",
                                "기억을 떠올리는 능력에 변화가 생겼다.",
                                5,
                                new com.example.projectmm.domain.simulation.StateDelta(
                                        0,
                                        5,
                                        -20
                                ),
                                null,
                                com.example.projectmm.domain.relationship.RelationshipDelta.none()
                        );

        CatchUpOrchestrator memoryOrchestrator =
                new CatchUpOrchestrator(
                        new CatchUpSimulationService(),
                        new CatchUpSegmentService(),
                        new CatchUpEventCandidateService(),
                        new EventCandidateValidator(),
                        new AiContextBuilder(
                                memoryRecallService,
                                relationshipRepository
                        ),
                        memoryChangingGateway,
                        new AiDecisionValidator()
                );

        LocalDateTime last =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 21,
                        13, 0
                );

        Character character =
                new Character("Millie");

        CharacterState state =
                new CharacterState(
                        character,
                        80,
                        10,
                        80,
                        last
                );

        CatchUpExecutionResult result =
                memoryOrchestrator.execute(
                        state,
                        now
                );

        assertThat(result.resolvedEvents())
                .hasSize(1);

        ResolvedEvent resolved =
                result.resolvedEvents()
                        .getFirst();

        assertThat(resolved.source())
                .isEqualTo(EventSource.AI);

        assertThat(
                resolved.stateDelta()
                        .memoryAbilityChange()
        ).isEqualTo(-20);

        assertThat(state.getMemoryAbility())
                .isEqualTo(60);
    }

    @Test
    void AI_호출이_실패해도_CatchUp은_계속_진행된다() {

        AiGateway failingGateway =
                context -> {
                    throw new RuntimeException(
                            "Gemini unavailable"
                    );
                };

        CatchUpOrchestrator orchestrator =
                new CatchUpOrchestrator(
                        new CatchUpSimulationService(),
                        new CatchUpSegmentService(),
                        new CatchUpEventCandidateService(),
                        new EventCandidateValidator(),
                        new AiContextBuilder(
                                memoryRecallService,
                                relationshipRepository
                        ),
                        failingGateway,
                        new AiDecisionValidator()
                );

        Character character =
                new Character("Millie");

        LocalDateTime from =
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                );

        LocalDateTime currentTime =
                LocalDateTime.of(
                        2026, 9, 21,
                        13, 0
                );

        CharacterState state =
                new CharacterState(
                        character,
                        80,
                        10,
                        from
                );

        CatchUpExecutionResult result =
                orchestrator.execute(
                        state,
                        currentTime
                );

        assertThat(result.candidates())
                .hasSize(1);

        assertThat(result.candidates().getFirst().requiresAi())
                .isTrue();

        assertThat(result.resolvedEvents())
                .isEmpty();

        assertThat(state.getEnergy())
                .isEqualTo(80);

        assertThat(state.getMood())
                .isEqualTo(10);

        assertThat(state.getSimulatedAt())
                .isEqualTo(currentTime);
    }
}