package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.ChatMemoryExtractor;
import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import java.util.List;

public class NoOpChatMemoryExtractor implements ChatMemoryExtractor {
    @Override
    public List<ChatMemoryCandidate> extract(String spokenText) {
        return List.of();
    }
}
