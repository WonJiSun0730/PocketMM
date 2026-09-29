package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.SemanticMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticMemoryDocumentTest {

    @Test
    void buildsCanonicalDocumentFromSemanticMemory() {

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

        String document =
                SemanticMemoryDocument.from(
                        memory
                );

        assertThat(document)
                .isEqualTo(
                        "TYPE_PREFERENCE "
                                + "SUBJECT_SELF "
                                + "RELATION_LIKE "
                                + "OBJECT_PEACH "
                                + "POLARITY_POSITIVE "
                                + "TEMPORALITY_TIMELESS "
                                + "CERTAINTY_CERTAIN"
                );
    }

    @Test
    void doesNotLeakSourceLanguageLiteralIntoCanonicalDocument() {

        SemanticMemory memory =
                new SemanticMemory(
                        SemanticMemory.Type.RELATIONSHIP,
                        "SELF",
                        "PET_NAME",
                        "LITERAL:감자",
                        SemanticMemory.Polarity.NEUTRAL,
                        SemanticMemory.Temporality.PRESENT,
                        SemanticMemory.Certainty.CERTAIN
                );

        String document =
                SemanticMemoryDocument.from(
                        memory
                );

        assertThat(document)
                .contains(
                        "TYPE_RELATIONSHIP",
                        "SUBJECT_SELF",
                        "RELATION_PET_NAME",
                        "OBJECT_LITERAL"
                )
                .doesNotContain("감자");
    }

    @Test
    void missingSemanticMemoryProducesEmptyDocument() {

        assertThat(
                SemanticMemoryDocument.from(null)
        ).isEmpty();
    }
}