package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.ChatMemoryExtractor;
import com.example.projectmm.infrastructure.ai.TextProviderRouting;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PocketMemoryProfileDiagnosisTest {
    private AnnotationConfigApplicationContext context(boolean openai,Map<String,Object> properties,String... profiles) {
        var context=new AnnotationConfigApplicationContext();context.getEnvironment().setActiveProfiles(profiles);
        var isolated=new java.util.HashMap<String,Object>();isolated.put("gemini.api-key", "");isolated.put("openai.api-key", "");isolated.putAll(properties);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("diagnosis",isolated));
        context.registerBean(ObjectMapper.class,()->new ObjectMapper());
        var routing=mock(TextProviderRouting.class);when(routing.openAi()).thenReturn(openai);
        context.registerBean(TextProviderRouting.class,()->routing);context.registerBean(AiUsageTracker.class,()->new AiUsageTracker());
        context.register(MemoryExtractionConfiguration.class);context.refresh();return context;
    }
    private Object selected(AnnotationConfigApplicationContext context) {
        return AiUsageTracker.withScope(7L,"MEMORY_EXTRACTION",()->ReflectionTestUtils.invokeMethod(context.getBean(ChatMemoryExtractor.class),"selected"));
    }
    @Test void prodWithGeminiSelectionUsesGemini() {
        try(var c=context(false,Map.of("gemini.api-key","test"),"prod")) {assertThat(selected(c)).isInstanceOf(GeminiChatMemoryExtractor.class);}
    }
    @Test void openAiOnlyProdNeedsNoGeminiConfiguration() {
        try(var c=context(true,Map.of("openai.api-key","test"),"prod")) {assertThat(selected(c)).isInstanceOf(OpenAiChatMemoryExtractor.class);}
    }
    @Test void bothKeysFollowSelectedProvider() {
        try(var c=context(true,Map.of("gemini.api-key","gemini-test","openai.api-key","openai-test"),"prod")) {assertThat(selected(c)).isInstanceOf(OpenAiChatMemoryExtractor.class);}
    }
    @Test void missingSelectedKeyBlocksInsteadOfUsingOtherConfiguredKey() {
        try(var c=context(true,Map.of("gemini.api-key","test"),"prod","gemini")) {
            assertThatThrownBy(()->AiUsageTracker.withScope(7L,"MEMORY_EXTRACTION",()->c.getBean(ChatMemoryExtractor.class).extractNarrative("허락 없이 접촉하지 않겠습니다.")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("fallback blocked");
        }
    }
}
