package com.example.projectmm.application.memory;

import com.example.projectmm.infrastructure.memory.JpaMemoryEmbeddingStore;
import com.example.projectmm.infrastructure.memory.MemoryEmbeddingJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MemoryRetrievalConfigurationTest {

    @Test
    void productionRetrievalBeansAreWired() {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.registerBean(
                    MemoryEmbeddingJpaRepository.class,
                    () -> mock(
                            MemoryEmbeddingJpaRepository.class
                    )
            );

            context.registerBean(
                    ObjectMapper.class,
                    () -> new ObjectMapper()
            );

            context.register(
                    MemoryRetrievalConfiguration.class
            );

            context.refresh();

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
                            JpaMemoryEmbeddingStore.class
                    );

            assertThat(cachedMemoryEmbeddingService)
                    .isNotNull();

            assertThat(memoryRetrievalRanker)
                    .isNotNull();
        }
    }
}