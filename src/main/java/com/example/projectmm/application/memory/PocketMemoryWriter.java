package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.WorldRoomSession;
import com.example.projectmm.domain.character.Character;
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
public class PocketMemoryWriter {

    private final MemoryRepository memories;
    private final MemoryDetailService memoryDetailService;

    @Transactional
    public ChatMemoryResult saveUserSpeech(
            WorldRoomSession session,
            Character character,
            String spoken,
            List<ChatMemoryCandidate> candidates,
            LocalDateTime at
    ) {
        if (session == null || session.getId() == null || character == null || character.getId() == null) {
            throw new IllegalArgumentException("A saved Pocket session and character are required");
        }
        if (spoken == null || spoken.isBlank() || candidates == null) {
            return ChatMemoryResult.empty("NONE");
        }

        List<String> saved = new ArrayList<>();
        int accepted = 0;
        for (ChatMemoryCandidate candidate : candidates) {
            if (candidate == null || candidate.quote() == null) continue;
            String quote = candidate.quote().strip();
            if (quote.length() < 2 || quote.length() > 400 || !spoken.contains(quote)
                    || candidate.importance() < 4 || candidate.importance() > 10
                    || (candidate.fact() != null && !candidate.fact().groundedIn(quote))) continue;
            if (++accepted > 3) break;

            String fingerprint = fingerprint(session.getId(), character.getId(), quote);
            if (memories.existsByChatFingerprint(fingerprint)) continue;

            Memory memory = Memory.fromWorldRoomUserSpeech(
                    session,
                    character,
                    quote,
                    candidate.importance(),
                    at,
                    fingerprint,
                    candidate.fact()
            );
            if (candidate.semanticMemory() != null) {
                memory.attachSemanticMemory(candidate.semanticMemory());
            }
            Memory persisted = memories.save(memory);
            memoryDetailService.saveDetails(persisted, candidate.details());
            saved.add(persisted.getSummary());
        }
        return new ChatMemoryResult(saved.isEmpty() ? "NONE" : "SAVED", List.copyOf(saved));
    }

    private String fingerprint(Long sessionId, Long characterId, String quote) {
        try {
            String text = "pocket|" + sessionId + "|" + characterId + "|" + quote.replaceAll("\\s+", " ");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
