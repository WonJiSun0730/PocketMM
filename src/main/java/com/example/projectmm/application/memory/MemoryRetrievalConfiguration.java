package com.example.projectmm.application.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
    public MemoryEmbeddingStore memoryEmbeddingStore() {
        return new InMemoryMemoryEmbeddingStore();
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