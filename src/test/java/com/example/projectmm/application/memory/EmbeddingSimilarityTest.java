package com.example.projectmm.application.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingSimilarityTest {

    @Test
    void identicalVectorsHaveMaximumSimilarity() {

        double similarity =
                EmbeddingSimilarity.cosine(
                        List.of(
                                1.0,
                                2.0,
                                3.0
                        ),
                        List.of(
                                1.0,
                                2.0,
                                3.0
                        )
                );

        assertThat(similarity)
                .isCloseTo(
                        1.0,
                        within(0.000001)
                );
    }

    @Test
    void orthogonalVectorsHaveZeroSimilarity() {

        double similarity =
                EmbeddingSimilarity.cosine(
                        List.of(
                                1.0,
                                0.0
                        ),
                        List.of(
                                0.0,
                                1.0
                        )
                );

        assertThat(similarity)
                .isCloseTo(
                        0.0,
                        within(0.000001)
                );
    }

    @Test
    void invalidVectorsReturnZero() {

        assertThat(
                EmbeddingSimilarity.cosine(
                        List.of(1.0),
                        List.of(1.0, 2.0)
                )
        ).isZero();

        assertThat(
                EmbeddingSimilarity.cosine(
                        List.of(),
                        List.of()
                )
        ).isZero();

        assertThat(
                EmbeddingSimilarity.cosine(
                        List.of(0.0, 0.0),
                        List.of(0.0, 0.0)
                )
        ).isZero();
    }

    private static org.assertj.core.data.Offset<Double> within(
            double value
    ) {
        return org.assertj.core.data.Offset.offset(
                value
        );
    }
}