package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.memory.MemoryEmbeddingProvider;
import com.example.projectmm.application.memory.MemoryRetrievalConfiguration;
import com.example.projectmm.application.memory.MemoryRetriever;
import com.example.projectmm.application.memory.HybridMemoryRetriever;
import com.example.projectmm.infrastructure.memory.MemoryEmbeddingJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiMemoryEmbeddingWiringTest {

    @Test
    void geminiProfileUsesGeminiEmbeddingProvider() {

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            ConfigurableEnvironment environment =
                    context.getEnvironment();

            environment.setActiveProfiles("gemini");

            environment.getSystemProperties().put(
                    "gemini.api-key",
                    "test-key"
            );

            context.registerBean(
                    ObjectMapper.class,
                    () -> new ObjectMapper()
            );

            context.registerBean(
                    MemoryEmbeddingJpaRepository.class,
                    () -> Mockito.mock(MemoryEmbeddingJpaRepository.class)
            );

            context.register(
                    MemoryRetrievalConfiguration.class,
                    GeminiMemoryEmbeddingProvider.class
            );

            context.refresh();

            MemoryEmbeddingProvider embeddingProvider =
                    context.getBean(
                            MemoryEmbeddingProvider.class
                    );

            MemoryRetriever memoryRetriever =
                    context.getBean(
                            MemoryRetriever.class
                    );

            assertThat(embeddingProvider)
                    .isInstanceOf(
                            GeminiMemoryEmbeddingProvider.class
                    );

            assertThat(memoryRetriever)
                    .isInstanceOf(
                            HybridMemoryRetriever.class
                    );
        }
    }
}