package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.ChatMemoryExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class MemoryExtractionConfiguration {
    private static final Logger log=LoggerFactory.getLogger(MemoryExtractionConfiguration.class);

    @Bean
    public ChatMemoryExtractor chatMemoryExtractor(Environment environment, ObjectMapper mapper,
            com.example.projectmm.infrastructure.ai.TextProviderRouting routing,
            com.example.projectmm.infrastructure.dialogue.AiUsageTracker usage) {
        var gemini=new GeminiChatMemoryExtractor(environment.getProperty("gemini.api-key", ""),
                environment.getProperty("gemini.memory-model", "gemini-3.5-flash-lite"),mapper);
        var openai=new OpenAiChatMemoryExtractor(environment.getProperty("openai.api-key", ""),
                environment.getProperty("openai.memory-model", "gpt-5.6-luna"),mapper);
        gemini.setUsageTracker(usage);openai.setUsageTracker(usage);
        return new ChatMemoryExtractor() {
            private ChatMemoryExtractor selected() {
                // Legacy chat has no Pocket provider choice. Preserve its existing key-based backend.
                if(com.example.projectmm.infrastructure.dialogue.AiUsageTracker.currentSessionId()==null)
                    return !environment.getProperty("gemini.api-key", "").isBlank()?gemini:
                            !environment.getProperty("openai.api-key", "").isBlank()?openai:new NoOpChatMemoryExtractor();
                return routing.openAi()?openai:gemini;
            }
            public java.util.List<com.example.projectmm.domain.memory.ChatMemoryCandidate> extract(String text) {return selected().extract(text);}
            public java.util.List<com.example.projectmm.domain.memory.ChatMemoryCandidate> extractNarrative(String text) {return selected().extractNarrative(text);}
        };
    }
}
