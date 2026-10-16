package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.application.ai.AiGateway;
import com.example.projectmm.application.chat.CatchUpNarrativeGateway;
import com.example.projectmm.application.chat.FallbackCatchUpNarrativeGateway;
import com.example.projectmm.application.time.AiTimeParser;
import com.example.projectmm.infrastructure.time.GeminiTimeParser;
import com.example.projectmm.infrastructure.time.NoOpAiTimeParser;
import com.example.projectmm.application.memory.MemoryEmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class GeminiFeatureAvailabilityTest {
    private AnnotationConfigApplicationContext context(String key,String... profiles) {
        var context=new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of(
                "openai.api-key","","gemini.api-key",key,"gemini.model","test-model","gemini.time-model","test-time")));
        context.registerBean(ObjectMapper.class,()->new ObjectMapper());
        context.register(GeminiAiGateway.class,MockAiGateway.class,GeminiTimeParser.class,NoOpAiTimeParser.class,
                GeminiCatchUpNarrativeGateway.class,FallbackCatchUpNarrativeGateway.class,GeminiMemoryEmbeddingProvider.class);
        context.refresh();
        return context;
    }
    @Test void configuredProdSelectsActualFeaturesWithoutGeminiProfile() {
        try(var context=context("test-never-sent","prod")) {
            assertThat(context.getBean(AiGateway.class)).isInstanceOf(GeminiAiGateway.class);
            assertThat(context.getBean(AiTimeParser.class)).isInstanceOf(GeminiTimeParser.class);
            assertThat(context.getBean(CatchUpNarrativeGateway.class)).isInstanceOf(GeminiCatchUpNarrativeGateway.class);
            assertThat(context.getBean(MemoryEmbeddingProvider.class)).isInstanceOf(GeminiMemoryEmbeddingProvider.class);
        }
    }
    @Test void emptyKeysKeepFallbacksEvenWithLegacyGeminiProfile() {
        try(var context=context("  ","prod","gemini")) {
            assertThat(context.getBean(AiGateway.class)).isInstanceOf(MockAiGateway.class);
            assertThat(context.getBean(AiTimeParser.class)).isInstanceOf(NoOpAiTimeParser.class);
            assertThat(context.getBean(CatchUpNarrativeGateway.class)).isInstanceOf(FallbackCatchUpNarrativeGateway.class);
            assertThat(context.getBeansOfType(MemoryEmbeddingProvider.class)).isEmpty();
        }
    }
}
