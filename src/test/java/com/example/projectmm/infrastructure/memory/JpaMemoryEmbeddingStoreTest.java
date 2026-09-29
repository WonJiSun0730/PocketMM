package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.StoredMemoryEmbedding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JpaMemoryEmbeddingStoreTest {

    @Autowired
    private MemoryEmbeddingJpaRepository repository;

    private JpaMemoryEmbeddingStore store;

    @BeforeEach
    void setUp() {
        store = new JpaMemoryEmbeddingStore(
                repository,
                new ObjectMapper()
        );
    }

    @Test
    void embedding을_저장하고_다시_조회한다() {
        store.save(
                101L,
                new StoredMemoryEmbedding(
                        "fingerprint-a",
                        List.of(
                                0.1,
                                0.2,
                                0.3
                        )
                )
        );

        var found =
                store.find(101L);

        assertThat(found)
                .isPresent();

        assertThat(
                found.orElseThrow()
                        .documentFingerprint()
        ).isEqualTo(
                "fingerprint-a"
        );

        assertThat(
                found.orElseThrow()
                        .embedding()
        ).containsExactly(
                0.1,
                0.2,
                0.3
        );
    }

    @Test
    void 같은_memoryId를_다시_저장하면_기존_embedding을_교체한다() {
        store.save(
                101L,
                new StoredMemoryEmbedding(
                        "fingerprint-old",
                        List.of(
                                0.1,
                                0.2
                        )
                )
        );

        store.save(
                101L,
                new StoredMemoryEmbedding(
                        "fingerprint-new",
                        List.of(
                                0.7,
                                0.8,
                                0.9
                        )
                )
        );

        var found =
                store.find(101L)
                        .orElseThrow();

        assertThat(
                found.documentFingerprint()
        ).isEqualTo(
                "fingerprint-new"
        );

        assertThat(
                found.embedding()
        ).containsExactly(
                0.7,
                0.8,
                0.9
        );

        assertThat(
                repository.count()
        ).isEqualTo(1);
    }

    @Test
    void 저장되지_않은_memoryId는_empty를_반환한다() {
        assertThat(
                store.find(999L)
        ).isEmpty();
    }

    @Test
    void 깨진_embedding_JSON은_empty로_처리한다() {
        repository.save(
                new MemoryEmbeddingEntity(
                        101L,
                        "fingerprint-a",
                        "not-json"
                )
        );

        assertThat(
                store.find(101L)
        ).isEmpty();
    }
}