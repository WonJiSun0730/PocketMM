package com.example.projectmm.domain.relationship;

import com.example.projectmm.domain.character.Character;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelationshipTest {

    @Test
    void 관계_변화량을_적용한다() {

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        Relationship relationship =
                new Relationship(
                        millie,
                        marbas
                );

        RelationshipDelta delta =
                new RelationshipDelta(
                        10,
                        20,
                        5
                );

        relationship.apply(delta);

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
    void 관계_수치는_0과_100_사이로_제한된다() {

        Character millie =
                new Character("Millie");

        Character marbas =
                new Character("Marbas");

        Relationship relationship =
                new Relationship(
                        millie,
                        marbas
                );

        relationship.apply(
                new RelationshipDelta(
                        150,
                        -50,
                        200
                )
        );

        assertThat(
                relationship.getAffinity()
        ).isEqualTo(100);

        assertThat(
                relationship.getTrust()
        ).isZero();

        assertThat(
                relationship.getTension()
        ).isEqualTo(100);
    }
}