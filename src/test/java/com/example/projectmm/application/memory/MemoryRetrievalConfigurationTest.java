package com.example.projectmm.application.memory;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRetrievalConfigurationTest {

    @Test
    void productionRetrievalBeansAreWired() {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             MemoryRetrievalConfiguration.class
                     )) {

            MemoryRetriever memoryRetriever =
                    context.getBean(
                            MemoryRetriever.class
                    );

            MemoryEmbeddingProvider memoryEmbeddingProvider =
                    context.getBean(
                            MemoryEmbeddingProvider.class
                    );

            MemoryEmbeddingStore memoryEmbeddingStore =
                    context.getBean(
                            MemoryEmbeddingStore.class
                    );

            CachedMemoryEmbeddingService cachedMemoryEmbeddingService =
                    context.getBean(
                            CachedMemoryEmbeddingService.class
                    );

            MemoryRetrievalRanker memoryRetrievalRanker =
                    context.getBean(
                            MemoryRetrievalRanker.class
                    );

            assertThat(memoryRetriever)
                    .isInstanceOf(
                            HybridMemoryRetriever.class
                    );

            assertThat(memoryEmbeddingProvider)
                    .isInstanceOf(
                            UnavailableMemoryEmbeddingProvider.class
                    );

            assertThat(memoryEmbeddingStore)
                    .isInstanceOf(
                            InMemoryMemoryEmbeddingStore.class
                    );

            assertThat(cachedMemoryEmbeddingService)
                    .isNotNull();

            assertThat(memoryRetrievalRanker)
                    .isNotNull();
        }
    }
}