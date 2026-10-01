package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.WorldRoomSessionCastRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PocketMemoryService {

    private static final Logger log = LoggerFactory.getLogger(PocketMemoryService.class);

    private final ChatMemoryExtractor extractor;
    private final PocketMemoryWriter writer;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomSessionCastRepository casts;

    @Value("${chat.memory.enabled:true}")
    private boolean enabled = true;

    public void rememberUserSpeech(Long sessionId, String userMessage, LocalDateTime at) {
        if (!enabled || userMessage == null || userMessage.isBlank() || userMessage.length() > 4000) return;
        try {
            var session = sessions.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Pocket session not found: " + sessionId));
            var candidates = extractor.extract(userMessage);
            for (var cast : casts.findBySessionIdAndActiveTrueOrderByEnteredAtAscIdAsc(sessionId)) {
                writer.saveUserSpeech(session, cast.getCharacter(), userMessage, candidates, at);
            }
        } catch (RuntimeException ex) {
            // Pocket chat is already durable; memory capture must never turn it into a failed/retried turn.
            log.warn("Pocket memory capture failed ({})", ex.getClass().getSimpleName());
        }
    }
}
