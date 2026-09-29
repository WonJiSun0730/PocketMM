package com.example.projectmm.domain.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SemanticMemoryTest {

    @Test
    void createsCompleteSemanticMemory() {

        SemanticMemory memory =
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "LIKE",
                        "PEACH",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                );

        assertThat(memory.getType())
                .isEqualTo(SemanticMemory.Type.PREFERENCE);

        assertThat(memory.getSubject())
                .isEqualTo("SELF");

        assertThat(memory.getRelation())
                .isEqualTo("LIKE");

        assertThat(memory.getObject())
                .isEqualTo("PEACH");

        assertThat(memory.getPolarity())
                .isEqualTo(SemanticMemory.Polarity.POSITIVE);

        assertThat(memory.getTemporality())
                .isEqualTo(SemanticMemory.Temporality.TIMELESS);

        assertThat(memory.getCertainty())
                .isEqualTo(SemanticMemory.Certainty.CERTAIN);
    }

    @Test
    void rejectsIncompleteSemanticMemory() {

        assertThatThrownBy(() ->
                new SemanticMemory(
                        null,
                        "SELF",
                        "LIKE",
                        "PEACH",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        " ",
                        "LIKE",
                        "PEACH",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "",
                        "PEACH",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "LIKE",
                        null,
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "LIKE",
                        "PEACH",
                        null,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "LIKE",
                        "PEACH",
                        SemanticMemory.Polarity.POSITIVE,
                        null,
                        SemanticMemory.Certainty.CERTAIN
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "LIKE",
                        "PEACH",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trimsCanonicalValues() {

        SemanticMemory memory =
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        " SELF ",
                        " LIKE ",
                        " PEACH ",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                );

        assertThat(memory.getSubject()).isEqualTo("SELF");
        assertThat(memory.getRelation()).isEqualTo("LIKE");
        assertThat(memory.getObject()).isEqualTo("PEACH");
    }

    @Test
    void attachesSemanticMemoryToMemoryOnlyOnce() {

        var semantic =
                new SemanticMemory(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "LIKE",
                        "PEACH",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS,
                        SemanticMemory.Certainty.CERTAIN
                );

        Memory memory =
                new Memory(
                        null,
                        null,
                        "test memory",
                        5,
                        50,
                        java.time.LocalDateTime.of(
                                2026,
                                9,
                                29,
                                12,
                                0
                        )
                );

        memory.attachSemanticMemory(semantic);

        assertThat(memory.getSemanticMemory())
                .isSameAs(semantic);

        assertThatThrownBy(() ->
                memory.attachSemanticMemory(
                        new SemanticMemory(
                                SemanticMemory.Type.PREFERENCE,
                                "SELF",
                                "DISLIKE",
                                "APPLE",
                                SemanticMemory.Polarity.NEGATIVE,
                                SemanticMemory.Temporality.TIMELESS,
                                SemanticMemory.Certainty.CERTAIN
                        )
                )
        ).isInstanceOf(IllegalStateException.class);
    }
}