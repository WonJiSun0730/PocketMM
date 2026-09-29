package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.CachedMemoryEmbeddingService;
import com.example.projectmm.application.memory.MemoryEmbeddingProvider;
import com.example.projectmm.domain.memory.SemanticMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CachedMemoryEmbeddingJpaIntegrationTest {

    @Autowired
    private MemoryEmbeddingJpaRepository repository;

    private CountingEmbeddingProvider embeddingProvider;
    private CachedMemoryEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingProvider =
                new CountingEmbeddingProvider();

        JpaMemoryEmbeddingStore embeddingStore =
                new JpaMemoryEmbeddingStore(
                        repository,
                        new ObjectMapper()
                );

        service =
                new CachedMemoryEmbeddingService(
                        embeddingProvider,
                        embeddingStore
                );
    }

    @Test
    void 저장된_embedding은_다음_호출에서_provider를_다시_호출하지_않는다() {
        SemanticMemory semanticMemory =
                semanticMemory(
                        "SELF",
                        "LIKE",
                        "COFFEE"
                );

        List<Double> first =
                service.embeddingFor(
                        101L,
                        semanticMemory
                );

        List<Double> second =
                service.embeddingFor(
                        101L,
                        semanticMemory
                );

        assertThat(first)
                .containsExactly(
                        0.1,
                        0.2,
                        0.3
                );

        assertThat(second)
                .isEqualTo(first);

        assertThat(
                embeddingProvider.callCount()
        ).isEqualTo(1);

        assertThat(
                repository.count()
        ).isEqualTo(1);
    }

    @Test
    void semanticMemory가_변경되면_embedding을_다시_생성하고_기존_row를_교체한다() {
        SemanticMemory firstSemanticMemory =
                semanticMemory(
                        "SELF",
                        "LIKE",
                        "COFFEE"
                );

        SemanticMemory changedSemanticMemory =
                semanticMemory(
                        "SELF",
                        "LIKE",
                        "TEA"
                );

        service.embeddingFor(
                101L,
                firstSemanticMemory
        );

        List<Double> changed =
                service.embeddingFor(
                        101L,
                        changedSemanticMemory
                );

        assertThat(changed)
                .containsExactly(
                        0.2,
                        0.4,
                        0.6
                );

        assertThat(
                embeddingProvider.callCount()
        ).isEqualTo(2);

        assertThat(
                repository.count()
        ).isEqualTo(1);
    }

    private SemanticMemory semanticMemory(
            String subject,
            String relation,
            String object
    ) {
        return new SemanticMemory(
                SemanticMemory.Type.PREFERENCE,
                subject,
                relation,
                object,
                SemanticMemory.Polarity.POSITIVE,
                SemanticMemory.Temporality.TIMELESS,
                SemanticMemory.Certainty.CERTAIN
        );
    }

    private static class CountingEmbeddingProvider
            implements MemoryEmbeddingProvider {

        private final AtomicInteger calls =
                new AtomicInteger();

        @Override
        public List<Double> embed(
                String text
        ) {
            int call =
                    calls.incrementAndGet();

            return List.of(
                    0.1 * call,
                    0.2 * call,
                    0.3 * call
            );
        }

        int callCount() {
            return calls.get();
        }
    }
}