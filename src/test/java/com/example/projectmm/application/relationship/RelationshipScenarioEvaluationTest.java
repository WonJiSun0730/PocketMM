package com.example.projectmm.application.relationship;

import com.example.projectmm.application.ai.AiContextBuilder;
import com.example.projectmm.application.memory.MemoryReactivationContextService;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.relationship.RelationshipFact;
import com.example.projectmm.domain.relationship.RelationshipFactRepository;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import com.example.projectmm.domain.simulation.EventCandidate;
import com.example.projectmm.domain.simulation.StateDelta;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false
)
@Import(RelationshipFactService.class)
class RelationshipScenarioEvaluationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RelationshipFactService relationshipFactService;

    @Autowired
    private RelationshipFactRepository relationshipFactRepository;

    @MockitoBean
    private MemoryRecallService memoryRecallService;

    @MockitoBean
    private RelationshipRepository relationshipRepository;

    @MockitoBean
    private MemoryReactivationContextService memoryReactivationContextService;

    @Test
    void 시나리오_같은_관계차원의_새_사실은_과거값을_중복보존하지_않고_현재상태를_갱신한다() {

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        em.persist(millie);
        em.persist(marbas);
        em.flush();

        LocalDateTime datingAt =
                LocalDateTime.of(
                        2026, 1, 10,
                        12, 0
                );

        RelationshipFact first =
                relationshipFactService.establish(
                        millie,
                        marbas,
                        "relationship_status",
                        "lover",
                        null,
                        datingAt
                );

        Long originalFactId =
                first.getId();

        LocalDateTime marriedAt =
                LocalDateTime.of(
                        2026, 2, 15,
                        18, 0
                );

        relationshipFactService.establish(
                millie,
                marbas,
                "relationship_status",
                "spouse",
                null,
                marriedAt
        );

        em.flush();
        em.clear();

        var facts =
                relationshipFactRepository
                        .findByFromCharacterIdAndToCharacterId(
                                millie.getId(),
                                marbas.getId()
                        );

        assertThat(facts)
                .hasSize(1);

        RelationshipFact current =
                facts.getFirst();

        assertThat(current.getId())
                .isEqualTo(originalFactId);

        assertThat(current.getRelation())
                .isEqualTo("relationship_status");

        assertThat(current.getValue())
                .isEqualTo("spouse");

        assertThat(current.getEstablishedAt())
                .isEqualTo(marriedAt);
    }

    @Test
    void 시나리오_서로_다른_관계차원의_사실은_동시에_현재상태로_보존된다() {

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        em.persist(millie);
        em.persist(marbas);
        em.flush();

        LocalDateTime establishedAt =
                LocalDateTime.of(
                        2026, 2, 15,
                        18, 0
                );

        relationshipFactService.establish(
                millie,
                marbas,
                "relationship_status",
                "spouse",
                null,
                establishedAt
        );

        relationshipFactService.establish(
                millie,
                marbas,
                "legal_status",
                "married",
                null,
                establishedAt
        );

        em.flush();
        em.clear();

        var facts =
                relationshipFactRepository
                        .findByFromCharacterIdAndToCharacterId(
                                millie.getId(),
                                marbas.getId()
                        );

        assertThat(facts)
                .hasSize(2);

        assertThat(facts)
                .extracting(
                        RelationshipFact::getRelation,
                        RelationshipFact::getValue
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "relationship_status",
                                "spouse"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "legal_status",
                                "married"
                        )
                );
    }

    @Test
    void 시나리오_갱신된_현재_관계사실만_CatchUp_AI_Context에_전달된다() {

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        em.persist(millie);
        em.persist(marbas);
        em.flush();

        LocalDateTime datingAt =
                LocalDateTime.of(
                        2026, 1, 10,
                        12, 0
                );

        relationshipFactService.establish(
                millie,
                marbas,
                "relationship_status",
                "lover",
                null,
                datingAt
        );

        LocalDateTime marriedAt =
                LocalDateTime.of(
                        2026, 2, 15,
                        18, 0
                );

        relationshipFactService.establish(
                millie,
                marbas,
                "relationship_status",
                "spouse",
                null,
                marriedAt
        );

        em.flush();
        em.clear();

        Character managedMillie =
                em.find(
                        Character.class,
                        millie.getId()
                );

        CharacterState state =
                new CharacterState(
                        managedMillie,
                        80,
                        70,
                        marriedAt
                );

        EventCandidate candidate =
                new EventCandidate(
                        marriedAt,
                        "TEST",
                        "현재 관계 사실 확인",
                        4,
                        true,
                        StateDelta.none()
                );

        when(
                memoryRecallService.recall(
                        managedMillie.getId(),
                        marriedAt,
                        5
                )
        ).thenReturn(List.of());

        when(
                relationshipRepository.findByFromCharacterId(
                        managedMillie.getId()
                )
        ).thenReturn(List.of());

        AiContextBuilder aiContextBuilder =
                new AiContextBuilder(
                        memoryRecallService,
                        relationshipRepository,
                        memoryReactivationContextService,
                        relationshipFactRepository
                );

        var context =
                aiContextBuilder.build(
                        state,
                        candidate
                );

        assertThat(context.relationshipFacts())
                .hasSize(1);

        assertThat(context.relationshipFacts().getFirst().targetCharacterName())
                .isEqualTo("Marbas");

        assertThat(context.relationshipFacts().getFirst().relation())
                .isEqualTo("relationship_status");

        assertThat(context.relationshipFacts().getFirst().value())
                .isEqualTo("spouse");

        assertThat(context.relationshipFacts())
                .noneMatch(fact ->
                        fact.value().equals("lover")
                );
    }
}