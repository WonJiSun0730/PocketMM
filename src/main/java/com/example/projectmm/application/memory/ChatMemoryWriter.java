package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMemoryWriter {
    private final MemoryRepository memories;
    private final ConversationRepository conversations;

    @Transactional
    public ChatMemoryResult save(Conversation conversation, ChatMessage source, String spoken,
                                 List<ChatMemoryCandidate> candidates, LocalDateTime at) {
        if (source.getId() == null || source.getRole() != ChatRole.USER
                || source.getConversation() == null
                || !conversation.getId().equals(source.getConversation().getId())) {
            throw new IllegalArgumentException("A saved user message from this conversation is required");
        }
        if (candidates == null) throw new IllegalArgumentException("Missing extraction result");
        // Serialize duplicate checks only while persisting, never while waiting for Gemini.
        conversations.lockForMemory(conversation.getId()).orElseThrow();
        List<String> saved = new ArrayList<>();
        int accepted = 0;
        for (ChatMemoryCandidate candidate : candidates) {
            if (candidate == null || candidate.quote() == null) continue;
            String quote = candidate.quote().strip();
            if (quote.length() < 2 || quote.length() > 400 || !spoken.contains(quote)
                    || candidate.importance() < 4 || candidate.importance() > 10) continue;
            if (++accepted > 3) break;
            String key = fingerprint(conversation.getId(), quote);
            if (memories.existsByChatFingerprint(key)) continue;
            Memory memory = Memory.fromChat(conversation, source, quote, candidate.importance(), at, key);
            memories.save(memory);
            saved.add(memory.getSummary());
        }
        return new ChatMemoryResult(saved.isEmpty() ? "NONE" : "SAVED", List.copyOf(saved));
    }

    private String fingerprint(Long conversationId, String quote) {
        try {
            String text = conversationId + "|" + quote.replaceAll("\\s+", " ");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
