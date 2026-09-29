package com.example.projectmm.domain.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryQuerySemanticTest {

    @Test
    void createsPartialSemanticIntent() {

        MemoryQuerySemantic query =
                MemoryQuerySemantic.intent(
                        SemanticMemory.Type.PREFERENCE,
                        "SELF",
                        "LIKE"
                );

        assertThat(query.type())
                .isEqualTo(
                        SemanticMemory.Type.PREFERENCE
                );

        assertThat(query.subject())
                .isEqualTo("SELF");

        assertThat(query.relation())
                .isEqualTo("LIKE");

        assertThat(query.object())
                .isNull();

        assertThat(query.polarity())
                .isNull();

        assertThat(query.temporality())
                .isNull();
    }

    @Test
    void trimsProvidedCanonicalValues() {

        MemoryQuerySemantic query =
                new MemoryQuerySemantic(
                        SemanticMemory.Type.PREFERENCE,
                        " SELF ",
                        " LIKE ",
                        " PEACH ",
                        SemanticMemory.Polarity.POSITIVE,
                        SemanticMemory.Temporality.TIMELESS
                );

        assertThat(query.subject())
                .isEqualTo("SELF");

        assertThat(query.relation())
                .isEqualTo("LIKE");

        assertThat(query.object())
                .isEqualTo("PEACH");
    }

    @Test
    void rejectsMissingType() {

        assertThatThrownBy(
                () -> MemoryQuerySemantic.intent(
                        null,
                        "SELF",
                        "LIKE"
                )
        ).isInstanceOf(
                IllegalArgumentException.class
        );
    }
}