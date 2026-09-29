package com.example.projectmm.application.memory;

import com.example.projectmm.infrastructure.memory.JpaMemoryEmbeddingStore;
import com.example.projectmm.infrastructure.memory.MemoryEmbeddingJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class MemoryRetrievalConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryEmbeddingProvider.class)
    public MemoryEmbeddingProvider memoryEmbeddingProvider() {
        return new UnavailableMemoryEmbeddingProvider();
    }

    @Bean
    public MemoryRetrievalRanker memoryRetrievalRanker() {
        return new MemoryRetrievalRanker();
    }

    @Bean
    public MemoryEmbeddingStore memoryEmbeddingStore(
            MemoryEmbeddingJpaRepository repository,
            ObjectMapper objectMapper
    ) {
        return new JpaMemoryEmbeddingStore(
                repository,
                objectMapper
        );
    }

    @Bean
    public CachedMemoryEmbeddingService cachedMemoryEmbeddingService(
            MemoryEmbeddingProvider embeddingProvider,
            MemoryEmbeddingStore memoryEmbeddingStore
    ) {
        return new CachedMemoryEmbeddingService(
                embeddingProvider,
                memoryEmbeddingStore
        );
    }

    @Bean
    @Primary
    public MemoryRetriever memoryRetriever(
            MemoryEmbeddingProvider embeddingProvider,
            CachedMemoryEmbeddingService memoryEmbeddingService,
            MemoryRetrievalRanker fallbackRanker
    ) {
        return new HybridMemoryRetriever(
                embeddingProvider,
                memoryEmbeddingService,
                fallbackRanker
        );
    }
}