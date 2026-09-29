package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.SemanticMemory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachedMemoryEmbeddingServiceTest {

    @Test
    void reusesEmbeddingWhenSemanticDocumentIsUnchanged() {

        AtomicInteger calls =
                new AtomicInteger();

        MemoryEmbeddingProvider provider =
                text -> {
                    calls.incrementAndGet();
                    return List.of(
                            0.1,
                            0.2
                    );
                };

        CachedMemoryEmbeddingService service =
                new CachedMemoryEmbeddingService(
                        provider,
                        new InMemoryMemoryEmbeddingStore()
                );

        SemanticMemory memory =
                semantic("LIKE");

        List<Double> first =
                service.embeddingFor(
                        10L,
                        memory
                );

        List<Double> second =
                service.embeddingFor(
                        10L,
                        memory
                );

        assertThat(first)
                .containsExactly(
                        0.1,
                        0.2
                );

        assertThat(second)
                .containsExactly(
                        0.1,
                        0.2
                );

        assertThat(calls.get())
                .isEqualTo(1);
    }

    @Test
    void regeneratesEmbeddingWhenSemanticDocumentChanges() {

        AtomicInteger calls =
                new AtomicInteger();

        MemoryEmbeddingProvider provider =
                text -> List.of(
                        (double) calls.incrementAndGet()
                );

        CachedMemoryEmbeddingService service =
                new CachedMemoryEmbeddingService(
                        provider,
                        new InMemoryMemoryEmbeddingStore()
                );

        List<Double> first =
                service.embeddingFor(
                        10L,
                        semantic("LIKE")
                );

        List<Double> second =
                service.embeddingFor(
                        10L,
                        semantic("DISLIKE")
                );

        assertThat(first)
                .containsExactly(1.0);

        assertThat(second)
                .containsExactly(2.0);

        assertThat(calls.get())
                .isEqualTo(2);
    }

    @Test
    void missingSemanticMemoryDoesNotCallProvider() {

        AtomicInteger calls =
                new AtomicInteger();

        MemoryEmbeddingProvider provider =
                text -> {
                    calls.incrementAndGet();
                    return List.of(1.0);
                };

        CachedMemoryEmbeddingService service =
                new CachedMemoryEmbeddingService(
                        provider,
                        new InMemoryMemoryEmbeddingStore()
                );

        assertThat(
                service.embeddingFor(
                        10L,
                        null
                )
        ).isEmpty();

        assertThat(calls.get())
                .isZero();
    }

    private SemanticMemory semantic(
            String relation
    ) {
        return new SemanticMemory(
                SemanticMemory.Type.PREFERENCE,
                "SELF",
                relation,
                "PEACH",
                SemanticMemory.Polarity.POSITIVE,
                SemanticMemory.Temporality.TIMELESS,
                SemanticMemory.Certainty.CERTAIN
        );
    }
}