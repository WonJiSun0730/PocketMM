package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import java.util.List;

public interface ChatMemoryExtractor {
    List<ChatMemoryCandidate> extract(String spokenText);
}
