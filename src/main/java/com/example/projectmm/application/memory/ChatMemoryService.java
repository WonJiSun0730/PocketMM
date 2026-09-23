package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.Conversation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatMemoryService {
    private static final Logger log = LoggerFactory.getLogger(ChatMemoryService.class);
    private final ChatMemoryExtractor extractor;
    private final ChatMemoryWriter writer;

    @Value("${chat.memory.enabled:true}")
    private boolean enabled = true;

    // External AI call deliberately occurs outside the writer's short DB transaction.
    public ChatMemoryResult remember(Conversation conversation, ChatMessage source, LocalDateTime at) {
        if (!enabled) return ChatMemoryResult.empty("DISABLED");
        String spoken = SpokenText.from(source.getContent());
        if (spoken.isBlank() || spoken.length() > 4000) return ChatMemoryResult.empty("SKIPPED");
        try {
            return writer.save(conversation, source, spoken, extractor.extract(spoken), at);
        } catch (RuntimeException ex) {
            // The chat has already been saved. Do not turn a memory failure into a failed chat/retry.
            log.warn("Chat memory capture failed ({})", ex.getClass().getSimpleName());
            return ChatMemoryResult.empty("FAILED");
        }
    }
}
