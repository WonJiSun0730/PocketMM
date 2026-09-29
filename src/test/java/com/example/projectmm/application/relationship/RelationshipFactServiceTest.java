package com.example.projectmm.application.relationship;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.relationship.RelationshipFact;
import com.example.projectmm.domain.relationship.RelationshipFactRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(RelationshipFactService.class)
class RelationshipFactServiceTest {

    @Autowired
    private RelationshipFactService service;

    @Autowired
    private RelationshipFactRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager em;

    @Test
    void sameRelationUpdatesCurrentFactInsteadOfCreatingDuplicate() {

        Character from =
                new Character("Millie");

        Character to =
                new Character("Marbas");

        em.persist(from);
        em.persist(to);
        em.flush();

        LocalDateTime datingAt =
                LocalDateTime.of(
                        2026, 1, 1,
                        12, 0
                );

        RelationshipFact first =
                service.establish(
                        from,
                        to,
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

        RelationshipFact updated =
                service.establish(
                        from,
                        to,
                        "relationship_status",
                        "spouse",
                        null,
                        marriedAt
                );

        em.flush();
        em.clear();

        var facts =
                repository
                        .findByFromCharacterIdAndToCharacterId(
                                from.getId(),
                                to.getId()
                        );

        assertThat(facts)
                .hasSize(1);

        RelationshipFact saved =
                facts.get(0);

        assertThat(saved.getId())
                .isEqualTo(first.getId());

        assertThat(updated.getId())
                .isEqualTo(first.getId());

        assertThat(saved.getRelation())
                .isEqualTo("relationship_status");

        assertThat(saved.getValue())
                .isEqualTo("spouse");

        assertThat(saved.getEstablishedAt())
                .isEqualTo(marriedAt);
    }

    @Test
    void differentRelationsCanCoexistForSameCharacters() {

        Character from =
                new Character("Millie");

        Character to =
                new Character("Marbas");

        em.persist(from);
        em.persist(to);
        em.flush();

        LocalDateTime establishedAt =
                LocalDateTime.of(
                        2026, 2, 15,
                        18, 0
                );

        service.establish(
                from,
                to,
                "relationship_status",
                "spouse",
                null,
                establishedAt
        );

        service.establish(
                from,
                to,
                "contract",
                "guardian-bound",
                null,
                establishedAt
        );

        em.flush();
        em.clear();

        var facts =
                repository
                        .findByFromCharacterIdAndToCharacterId(
                                from.getId(),
                                to.getId()
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
                                "contract",
                                "guardian-bound"
                        )
                );
    }
}