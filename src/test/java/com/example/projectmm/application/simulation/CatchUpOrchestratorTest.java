package com.example.projectmm.application.simulation;

import com.example.projectmm.application.ai.AiContextBuilder;
import com.example.projectmm.application.ai.AiDecisionValidator;
import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.application.memory.MemoryDetailCandidate;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.memory.MemoryDetailSalience;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import com.example.projectmm.domain.simulation.*;
import com.example.projectmm.infrastructure.ai.MockAiGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
                .hasSize(1);

        ResolvedEvent fallback =
                result.resolvedEvents().getFirst();

        assertThat(fallback.source())
                .isEqualTo(EventSource.RULE);

        assertThat(fallback.eventType())
                .isEqualTo(
                        result.candidates().getFirst().eventType()
                );

        assertThat(fallback.summary())
                .isEqualTo(
                        result.candidates().getFirst().summary()
                );

        assertThat(fallback.importance())
                .isEqualTo(
                        result.candidates().getFirst().importance()
                );

        assertThat(fallback.narrativeAnchor())
                .isZero();

        assertThat(fallback.relationshipTargetCharacterId())
                .isNull();

        assertThat(fallback.relationshipDelta())
                .isEqualTo(
                        com.example.projectmm.domain.relationship.RelationshipDelta.none()
                );

        assertThat(fallback.memoryAccessChange())
                .isNull();

        assertThat(state.getEnergy())
                .isEqualTo(80);

        assertThat(state.getMood())
                .isEqualTo(10);

        assertThat(state.getSimulatedAt())
                .isEqualTo(currentTime);
    }

    @Test
    void AI가_결정한_narrativeAnchor가_ResolvedEvent에_전달된다() {

        AiGateway anchorGateway =
                context ->
                        new com.example.projectmm.domain.ai.AiDecision(
                                "RELATIONSHIP_MILESTONE",
                                "장기적으로 기억되어야 할 중요한 약속을 나눴다.",
                                6,
                                9,
                                new com.example.projectmm.domain.simulation.StateDelta(
                                        0,
                                        5,
                                        0
                                ),
                                null,
                                com.example.projectmm.domain.relationship.RelationshipDelta.none(),
                                null
                        );

        CatchUpOrchestrator anchorOrchestrator =
                new CatchUpOrchestrator(
                        new CatchUpSimulationService(),
                        new CatchUpSegmentService(),
                        new CatchUpEventCandidateService(),
                        new EventCandidateValidator(),
                        new AiContextBuilder(
                                memoryRecallService,
                                relationshipRepository
                        ),
                        anchorGateway,
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
                        last
                );

        CatchUpExecutionResult result =
                anchorOrchestrator.execute(
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

        assertThat(resolved.importance())
                .isEqualTo(6);

        assertThat(resolved.narrativeAnchor())
                .isEqualTo(9);
    }

    @Test
    void AI_첫_호출이_실패하면_한_번_재시도하고_성공한_결정을_적용한다() {

        AtomicInteger attempts =
                new AtomicInteger();

        AiGateway retryGateway =
                context -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new RuntimeException(
                                "temporary failure"
                        );
                    }

                    return new com.example.projectmm.domain.ai.AiDecision(
                            "RETRY_SUCCESS",
                            "재시도에서 정상적으로 생성된 사건",
                            5,
                            3,
                            new StateDelta(
                                    0,
                                    4,
                                    0
                            ),
                            null,
                            com.example.projectmm.domain.relationship.RelationshipDelta.none(),
                            null
                    );
                };

        CatchUpOrchestrator retryOrchestrator =
                createOrchestrator(retryGateway);

        CharacterState state =
                createAiRequiredState();

        CatchUpExecutionResult result =
                retryOrchestrator.execute(
                        state,
                        LocalDateTime.of(
                                2026, 9, 21,
                                13, 0
                        )
                );

        assertThat(attempts.get())
                .isEqualTo(2);

        assertThat(result.resolvedEvents())
                .hasSize(1);

        assertThat(result.resolvedEvents().getFirst().source())
                .isEqualTo(EventSource.AI);

        assertThat(result.resolvedEvents().getFirst().eventType())
                .isEqualTo("RETRY_SUCCESS");

        assertThat(state.getMood())
                .isEqualTo(14);
    }

    @Test
    void AI_결정이_검증에_실패하면_한_번_재시도하고_정상_결정을_적용한다() {

        AtomicInteger attempts =
                new AtomicInteger();

        AiGateway retryGateway =
                context -> {
                    if (attempts.incrementAndGet() == 1) {
                        return new com.example.projectmm.domain.ai.AiDecision(
                                "INVALID",
                                "잘못된 첫 결정",
                                999,
                                new StateDelta(
                                        0,
                                        999,
                                        0
                                ),
                                null,
                                com.example.projectmm.domain.relationship.RelationshipDelta.none()
                        );
                    }

                    return new com.example.projectmm.domain.ai.AiDecision(
                            "VALID_AFTER_RETRY",
                            "검증 실패 후 재생성된 정상 사건",
                            5,
                            new StateDelta(
                                    0,
                                    3,
                                    0
                            ),
                            null,
                            com.example.projectmm.domain.relationship.RelationshipDelta.none()
                    );
                };

        CatchUpOrchestrator retryOrchestrator =
                createOrchestrator(retryGateway);

        CharacterState state =
                createAiRequiredState();

        CatchUpExecutionResult result =
                retryOrchestrator.execute(
                        state,
                        LocalDateTime.of(
                                2026, 9, 21,
                                13, 0
                        )
                );

        assertThat(attempts.get())
                .isEqualTo(2);

        assertThat(result.resolvedEvents())
                .hasSize(1);

        assertThat(result.resolvedEvents().getFirst().eventType())
                .isEqualTo("VALID_AFTER_RETRY");

        assertThat(result.resolvedEvents().getFirst().source())
                .isEqualTo(EventSource.AI);

        assertThat(state.getMood())
                .isEqualTo(13);
    }

    @Test
    void AI_호출이_두_번_모두_실패하면_safe_fallback을_적용한다() {

        AtomicInteger attempts =
                new AtomicInteger();

        AiGateway failingGateway =
                context -> {
                    attempts.incrementAndGet();

                    throw new RuntimeException(
                            "AI unavailable"
                    );
                };

        CatchUpOrchestrator retryOrchestrator =
                createOrchestrator(failingGateway);

        CharacterState state =
                createAiRequiredState();

        CatchUpExecutionResult result =
                retryOrchestrator.execute(
                        state,
                        LocalDateTime.of(
                                2026, 9, 21,
                                13, 0
                        )
                );

        assertThat(attempts.get())
                .isEqualTo(2);

        assertThat(result.resolvedEvents())
                .hasSize(1);

        ResolvedEvent fallback =
                result.resolvedEvents().getFirst();

        assertThat(fallback.source())
                .isEqualTo(EventSource.RULE);

        assertThat(fallback.narrativeAnchor())
                .isZero();

        assertThat(fallback.relationshipTargetCharacterId())
                .isNull();

        assertThat(fallback.memoryAccessChange())
                .isNull();

        assertThat(fallback.memoryDetails())
                .isEmpty();
    }

    @Test
    void AI가_생성한_memoryDetails가_ResolvedEvent에_전달된다() {

        AiGateway detailGateway =
                context ->
                        new com.example.projectmm.domain.ai.AiDecision(
                                "RELATIONSHIP_MILESTONE",
                                "마르바스와 붉은 반지를 교환하며 약속을 나눴다.",
                                7,
                                8,
                                new StateDelta(
                                        0,
                                        5,
                                        0
                                ),
                                null,
                                com.example.projectmm.domain.relationship.RelationshipDelta.none(),
                                null,
                                List.of(
                                        new MemoryDetailCandidate(
                                                "붉은 반지",
                                                MemoryDetailSalience.HIGH,
                                                false
                                        ),
                                        new MemoryDetailCandidate(
                                                "약속",
                                                MemoryDetailSalience.CRITICAL,
                                                true
                                        )
                                )
                        );

        CatchUpOrchestrator detailOrchestrator =
                createOrchestrator(detailGateway);

        CharacterState state =
                createAiRequiredState();

        CatchUpExecutionResult result =
                detailOrchestrator.execute(
                        state,
                        LocalDateTime.of(
                                2026, 9, 21,
                                13, 0
                        )
                );

        assertThat(result.resolvedEvents())
                .hasSize(1);

        ResolvedEvent resolved =
                result.resolvedEvents().getFirst();

        assertThat(resolved.source())
                .isEqualTo(EventSource.AI);

        assertThat(resolved.memoryDetails())
                .hasSize(2);

        assertThat(resolved.memoryDetails().get(0).text())
                .isEqualTo("붉은 반지");

        assertThat(resolved.memoryDetails().get(0).salience())
                .isEqualTo(MemoryDetailSalience.HIGH);

        assertThat(resolved.memoryDetails().get(1).text())
                .isEqualTo("약속");

        assertThat(resolved.memoryDetails().get(1).salience())
                .isEqualTo(MemoryDetailSalience.CRITICAL);

        assertThat(resolved.memoryDetails().get(1).protectedAnchor())
                .isTrue();
    }

    private CatchUpOrchestrator createOrchestrator(
            AiGateway gateway
    ) {
        return new CatchUpOrchestrator(
                new CatchUpSimulationService(),
                new CatchUpSegmentService(),
                new CatchUpEventCandidateService(),
                new EventCandidateValidator(),
                new AiContextBuilder(
                        memoryRecallService,
                        relationshipRepository
                ),
                gateway,
                new AiDecisionValidator()
        );
    }

    private CharacterState createAiRequiredState() {

        Character character =
                new Character("Millie");

        return new CharacterState(
                character,
                80,
                10,
                LocalDateTime.of(
                        2026, 9, 21,
                        12, 0
                )
        );
    }
}