package com.example.projectmm.application.relationship;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RelationshipChangeServiceTest {

    @Test
    void 관계_변화량을_적용한다() {

        RelationshipRepository repository =
                mock(RelationshipRepository.class);

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        Relationship relationship =
                new Relationship(
                        millie,
                        marbas
                );

        when(
                repository.findByFromCharacterIdAndToCharacterId(
                        1L,
                        2L
                )
        ).thenReturn(
                Optional.of(relationship)
        );

        RelationshipChangeService service =
                new RelationshipChangeService(repository);

        service.apply(
                1L,
                2L,
                new RelationshipDelta(
                        10,
                        20,
                        5
                )
        );

        assertThat(
                relationship.getAffinity()
        ).isEqualTo(10);

        assertThat(
                relationship.getTrust()
        ).isEqualTo(20);

        assertThat(
                relationship.getTension()
        ).isEqualTo(5);
    }

    @Test
    void 대상이_null이면_관계를_조회하지_않는다() {

        RelationshipRepository repository =
                mock(RelationshipRepository.class);

        RelationshipChangeService service =
                new RelationshipChangeService(repository);

        service.apply(
                1L,
                null,
                new RelationshipDelta(
                        10,
                        10,
                        10
                )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void 변화량이_모두_0이면_관계를_조회하지_않는다() {

        RelationshipRepository repository =
                mock(RelationshipRepository.class);

        RelationshipChangeService service =
                new RelationshipChangeService(repository);

        service.apply(
                1L,
                2L,
                RelationshipDelta.none()
        );

        verifyNoInteractions(repository);
    }

    @Test
    void 존재하지_않는_관계는_예외가_발생한다() {

        RelationshipRepository repository =
                mock(RelationshipRepository.class);

        when(
                repository.findByFromCharacterIdAndToCharacterId(
                        1L,
                        999L
                )
        ).thenReturn(
                Optional.empty()
        );

        RelationshipChangeService service =
                new RelationshipChangeService(repository);

        assertThatThrownBy(() ->
                service.apply(
                        1L,
                        999L,
                        new RelationshipDelta(
                                5,
                                0,
                                0
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Relationship not found"
                );
    }
}