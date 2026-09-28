package com.example.projectmm.application.simulation;

import com.example.projectmm.application.ai.*;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.application.relationship.RelationshipChangeService;
import com.example.projectmm.domain.ai.AiDecision;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.memory.*;
import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.simulation.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({CatchUpExecutionService.class, CatchUpOrchestrator.class, CatchUpSimulationService.class,
        CatchUpSegmentService.class, EventCandidateValidator.class, AiDecisionValidator.class,
        RelationshipChangeService.class, MemoryAccessService.class, CatchUpSceneResolver.class,
        CatchUpSceneFactory.class, ResolvedEventPersistenceService.class, ResolvedEventMapper.class,
        MemoryCreationService.class, MemoryPolicy.class})
class MemoryAccessEventIntegrationTest {
    @Autowired CatchUpExecutionService execution;
    @Autowired MemoryAccessStateRepository accessStates;
    @Autowired EntityManager em;
    @MockitoBean AiGateway gateway;
    @MockitoBean AiContextBuilder contextBuilder;
    @MockitoBean CatchUpEventCandidateService candidates;
    private final LocalDateTime cutoff = LocalDateTime.of(2030, 1, 2, 12, 0);
    private Long stateId;
    private Long otherId;
    private Long relationshipId;
    private Long older;
    private Long boundary;
    private Long newer;
    private Long otherMemory;

    @BeforeEach
    void setUp() {
        var character = new Character("Current");
        var other = new Character("Other");
        em.persist(character);
        em.persist(other);
        otherId = other.getId();
        var state = new CharacterState(character, 70, 70, 90, cutoff.plusHours(1));
        em.persist(state);
        stateId = state.getId();
        var relationship = new Relationship(character, other);
        em.persist(relationship);
        relationshipId = relationship.getId();
        older = memory(character, cutoff.minusSeconds(1));
        boundary = memory(character, cutoff);
        newer = memory(character, cutoff.plusMinutes(1));
        otherMemory = memory(other, cutoff.plusMinutes(1));
        when(candidates.generate(any(), any())).thenAnswer(invocation -> {
            CatchUpSegment segment = invocation.getArgument(1);
            return List.of(new EventCandidate(segment.end(), "NOT_AN_AMNESIA_LABEL", "candidate", 5, true, StateDelta.none()));
        });
        em.flush();
        em.clear();
    }

    @Test
    void allMemoriesCanBeBlockedAndRestoredWithoutChangingAbilityOrOtherCharacter() {
        run(new MemoryAccessChange("ALL", "BLOCKED", null), StateDelta.none(), RelationshipDelta.none());
        assertThat(level(older)).isEqualTo(MemoryAccessLevel.BLOCKED);
        assertThat(level(boundary)).isEqualTo(MemoryAccessLevel.BLOCKED);
        assertThat(level(newer)).isEqualTo(MemoryAccessLevel.BLOCKED);
        assertThat(level(otherMemory)).isEqualTo(MemoryAccessLevel.EXPLICIT);
        assertThat(em.find(CharacterState.class, stateId).getMemoryAbility()).isEqualTo(90);
        run(new MemoryAccessChange("ALL", "EXPLICIT", null), StateDelta.none(), RelationshipDelta.none());
        assertThat(level(older)).isEqualTo(MemoryAccessLevel.EXPLICIT);
        assertThat(level(newer)).isEqualTo(MemoryAccessLevel.EXPLICIT);
        assertThat(em.find(Memory.class, older).getSummary()).isEqualTo("Original memory");
    }

    @Test
    void sinceIncludesBoundaryAndRestoresSameRangeWithoutTouchingOlderMemory() {
        run(new MemoryAccessChange("SINCE", "BLOCKED", cutoff.toString()), StateDelta.none(), RelationshipDelta.none());
        assertThat(accessStates.findByMemoryId(older)).isEmpty();
        assertThat(level(boundary)).isEqualTo(MemoryAccessLevel.BLOCKED);
        assertThat(level(newer)).isEqualTo(MemoryAccessLevel.BLOCKED);
        assertThat(accessStates.findByMemoryId(otherMemory)).isEmpty();
        run(new MemoryAccessChange("SINCE", "EXPLICIT", cutoff.toString()), StateDelta.none(), RelationshipDelta.none());
        assertThat(level(boundary)).isEqualTo(MemoryAccessLevel.EXPLICIT);
        assertThat(level(newer)).isEqualTo(MemoryAccessLevel.EXPLICIT);
        assertThat(accessStates.findByMemoryId(older)).isEmpty();
    }

    @Test
    void legacyAndCombinedDecisionsPreserveAbilityAndRelationshipDeltaApplication() {
        run(null, new StateDelta(-2, 1, -5), new RelationshipDelta(2, 3, -1));
        assertThat(accessStates.count()).isZero();
        assertThat(em.find(CharacterState.class, stateId).getMemoryAbility()).isEqualTo(85);
        assertThat(em.find(Relationship.class, relationshipId).getTrust()).isEqualTo(3);
        run(new MemoryAccessChange("ALL", "BLOCKED", null), new StateDelta(0, 0, 4), new RelationshipDelta(1, 2, 0));
        assertThat(em.find(CharacterState.class, stateId).getMemoryAbility()).isEqualTo(89);
        assertThat(em.find(Relationship.class, relationshipId).getTrust()).isEqualTo(5);
        assertThat(level(older)).isEqualTo(MemoryAccessLevel.BLOCKED);
    }

    @Test
    void invalidAccessRejectsEntireDecisionBeforeAnyDeltaOrEventIsApplied() {
        var state = em.find(CharacterState.class, stateId);
        when(gateway.decide(any())).thenReturn(new AiDecision("X", "event", 5, new StateDelta(-5, -5, -5),
                otherId, new RelationshipDelta(2, 3, 1), new MemoryAccessChange("ALL", "INVALID", null)));
        assertThat(execution.execute(state, state.getSimulatedAt().plusMinutes(1))).isEmpty();
        em.flush();
        em.clear();
        assertThat(accessStates.count()).isZero();
        assertThat(em.find(CharacterState.class, stateId).getMemoryAbility()).isEqualTo(90);
        assertThat(em.find(Relationship.class, relationshipId).getTrust()).isZero();
    }

    private void run(MemoryAccessChange change, StateDelta delta, RelationshipDelta relationship) {
        var state = em.find(CharacterState.class, stateId);
        when(gateway.decide(any())).thenReturn(new AiDecision("ANY_EVENT", "Resolved event", 5, delta, otherId, relationship, change));
        assertThat(execution.execute(state, state.getSimulatedAt().plusMinutes(1))).hasSize(1);
        em.flush();
        em.clear();
    }

    private Long memory(Character character, LocalDateTime time) {
        var memory = new Memory(character, null, "Original memory", 7, 70, time);
        em.persist(memory);
        return memory.getId();
    }

    private MemoryAccessLevel level(Long memoryId) {
        return accessStates.findByMemoryId(memoryId).map(MemoryAccessState::getAccessLevel).orElse(MemoryAccessLevel.EXPLICIT);
    }
}
