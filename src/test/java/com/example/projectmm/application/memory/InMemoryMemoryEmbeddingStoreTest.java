package com.example.projectmm.application.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMemoryEmbeddingStoreTest {

    private final InMemoryMemoryEmbeddingStore store =
            new InMemoryMemoryEmbeddingStore();

    @Test
    void savesAndFindsEmbedding() {

        StoredMemoryEmbedding embedding =
                new StoredMemoryEmbedding(
                        "fingerprint",
                        List.of(
                                0.1,
                                0.2,
                                0.3
                        )
                );

        store.save(
                10L,
                embedding
        );

        assertThat(
                store.find(10L)
        )
                .isPresent()
                .contains(embedding);
    }

    @Test
    void missingEmbeddingReturnsEmpty() {

        assertThat(
                store.find(999L)
        ).isEmpty();
    }

    @Test
    void invalidValuesAreNotStored() {

        StoredMemoryEmbedding embedding =
                new StoredMemoryEmbedding(
                        "fingerprint",
                        List.of(1.0)
                );

        store.save(
                null,
                embedding
        );

        store.save(
                10L,
                null
        );

        assertThat(
                store.find(10L)
        ).isEmpty();
    }
}