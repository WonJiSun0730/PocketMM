package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.ChatMemoryExtractor;
import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Profile("!gemini")
public class NoOpChatMemoryExtractor implements ChatMemoryExtractor {
    @Override
    public List<ChatMemoryCandidate> extract(String spokenText) {
        return List.of();
    }
}
