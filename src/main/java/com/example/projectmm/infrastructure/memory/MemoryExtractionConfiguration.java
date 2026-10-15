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
    public ChatMemoryExtractor chatMemoryExtractor(Environment environment, ObjectMapper mapper) {
        String gemini=environment.getProperty("gemini.api-key", "");
        String openai=environment.getProperty("openai.api-key", "");
        if (!gemini.isBlank()) {
            log.info("Memory extraction backend: GEMINI (independent of dialogue provider and profiles)");
            return new GeminiChatMemoryExtractor(gemini,
                    environment.getProperty("gemini.memory-model", "gemini-3.5-flash-lite"),mapper);
        }
        if (!openai.isBlank()) {
            log.info("Memory extraction backend: OPENAI (independent of dialogue provider and profiles)");
            return new OpenAiChatMemoryExtractor(openai,
                    environment.getProperty("openai.memory-model", "gpt-5.6-luna"),mapper);
        }
        log.warn("Memory extraction unavailable: neither Gemini nor OpenAI API key is configured");
        return new NoOpChatMemoryExtractor();
    }
}
