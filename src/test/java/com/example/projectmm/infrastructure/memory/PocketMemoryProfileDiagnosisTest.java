package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.ChatMemoryExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PocketMemoryProfileDiagnosisTest {
    private AnnotationConfigApplicationContext context(Map<String,Object> properties,String... profiles) {
        var context=new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles);
        var isolated=new java.util.HashMap<String,Object>();
        isolated.put("gemini.api-key", "");
        isolated.put("openai.api-key", "");
        isolated.putAll(properties);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("diagnosis",isolated));
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().build());
        context.register(MemoryExtractionConfiguration.class);
        context.refresh();
        return context;
    }
    @Test void prodWithGeminiKeySelectsRealExtractorWithoutGeminiProfile() {
        try(var context=context(Map.of("gemini.api-key","test-never-sent"),"prod")) {
            assertThat(context.getBean(ChatMemoryExtractor.class)).isInstanceOf(GeminiChatMemoryExtractor.class);
        }
    }
    @Test void openAiOnlyProdCanExtractWithoutAnyGeminiConfiguration() {
        try(var context=context(Map.of("openai.api-key","test-never-sent"),"prod")) {
            assertThat(context.getBean(ChatMemoryExtractor.class)).isInstanceOf(OpenAiChatMemoryExtractor.class);
        }
    }
    @Test void bothKeysPreserveExistingSmallGeminiExtractionBackend() {
        try(var context=context(Map.of("gemini.api-key","gemini-test","openai.api-key","openai-test"),"prod")) {
            assertThat(context.getBean(ChatMemoryExtractor.class)).isInstanceOf(GeminiChatMemoryExtractor.class);
        }
    }
    @Test void legacyProfileWithoutKeysDoesNotPretendExtractionIsAvailable() {
        try(var context=context(Map.of(),"prod","gemini")) {
            var extractor=context.getBean(ChatMemoryExtractor.class);
            assertThat(extractor).isInstanceOf(NoOpChatMemoryExtractor.class);
            assertThat(extractor.extractNarrative("허락 없이 접촉하지 않겠습니다.")).isEmpty();
        }
    }
}
