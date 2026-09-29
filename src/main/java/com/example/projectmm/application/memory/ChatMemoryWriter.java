package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ChatMemoryWriter {
    private final MemoryRepository memories;
    private final ConversationRepository conversations;
    private final MemoryDetailService memoryDetailService;

    @Autowired
    public ChatMemoryWriter(
            MemoryRepository memories,
            ConversationRepository conversations,
            MemoryDetailService memoryDetailService
    ) {
        this.memories = memories;
        this.conversations = conversations;
        this.memoryDetailService = memoryDetailService;
    }

    public ChatMemoryWriter(
            MemoryRepository memories,
            ConversationRepository conversations
    ) {
        this(
                memories,
                conversations,
                null
        );
    }

    @Transactional
    public ChatMemoryResult save(Conversation conversation, ChatMessage source, String spoken,
                                 List<ChatMemoryCandidate> candidates, LocalDateTime at) {
        if (source.getId() == null || source.getRole() != ChatRole.USER
                || source.getConversation() == null
                || !java.util.Objects.equals(conversation.getCharacter().getId(), source.getCharacter().getId())
                || !conversation.getId().equals(source.getConversation().getId())) {
            throw new IllegalArgumentException("A saved user message from this conversation is required");
        }
        if (candidates == null) throw new IllegalArgumentException("Missing extraction result");
        // 호출자가 전달한 추출 입력도 원문 발언 필터와 일치해야 한다. 내면 지문을 우회 저장하지 않는다.
        if (!SpokenText.from(source.getContent()).equals(spoken))
            throw new IllegalArgumentException("Extraction input must match source speech");
        // Serialize duplicate checks only while persisting, never while waiting for Gemini.
        var locked = conversations.lockForMemory(conversation.getId()).orElseThrow();
        if (!java.util.Objects.equals(locked.getCharacter().getId(), conversation.getCharacter().getId()))
            throw new IllegalArgumentException("Conversation character mismatch");
        List<String> saved = new ArrayList<>();
        int accepted = 0;
        for (ChatMemoryCandidate candidate : candidates) {
            if (candidate == null || candidate.quote() == null) continue;
            String quote = candidate.quote().strip();
            if (quote.length() < 2 || quote.length() > 400 || !spoken.contains(quote) || !isCompleteStatement(spoken, quote)
                    || candidate.importance() < 4 || candidate.importance() > 10
                    || (candidate.fact() != null && !candidate.fact().groundedIn(quote))) continue;
            if (++accepted > 3) break;
            String key = fingerprint(conversation.getId(), quote);
            if (memories.existsByChatFingerprint(key)) continue;
            Memory memory = Memory.fromChat(conversation, source, quote, candidate.importance(), at, key, candidate.fact());
            Memory persisted =
                    memories.save(memory);

            if (memoryDetailService != null) {
                memoryDetailService.saveDetails(
                        persisted,
                        candidate.details()
                );
            }

            saved.add(
                    persisted.getSummary()
            );
        }
        return new ChatMemoryResult(saved.isEmpty() ? "NONE" : "SAVED", List.copyOf(saved));
    }

    private boolean isCompleteStatement(String spoken, String quote) {
        // 문장 중간의 부정·가정·인용 맥락을 잘라내지 못하게 한다. fact:null로 우회하지 못하도록 공통 적용한다.
        // 의미 판별기가 아니므로 경계가 모호한 인용/질문은 저장하지 않는다.
        if (quote.indexOf('?') >= 0 || quote.indexOf('？') >= 0) return false;
        int start = spoken.indexOf(quote);
        while (start >= 0) {
            String before = spoken.substring(0, start).stripTrailing();
            int end = start + quote.length();
            boolean startsSentence = before.isEmpty() || before.endsWith(".")
                    || before.endsWith("!") || before.endsWith("?")
                    || spoken.substring(0, start).endsWith("\n");
            boolean endsSentence = end == spoken.length() || (quote.endsWith(".") || quote.endsWith("!"))
                    && Character.isWhitespace(spoken.charAt(end));
            if (startsSentence && endsSentence) return true;
            start = spoken.indexOf(quote, start + 1);
        }
        return false;
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
