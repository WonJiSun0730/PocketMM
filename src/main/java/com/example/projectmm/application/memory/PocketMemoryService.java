package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.WorldRoomSessionCastRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.domain.chat.WorldRoomMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PocketMemoryService {

    private static final Logger log = LoggerFactory.getLogger(PocketMemoryService.class);

    private final ChatMemoryExtractor extractor;
    private final PocketMemoryWriter writer;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomSessionCastRepository casts;

    @Value("${chat.memory.enabled:true}")
    private boolean enabled = true;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rememberUserSpeech(Long sessionId, Long sourceMessageId, String userMessage, LocalDateTime at) {
        if (!enabled || userMessage == null || userMessage.isBlank() || userMessage.length() > 4000) return;
        try {
            var session = sessions.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Pocket session not found: " + sessionId));
            var source = messages.findById(sourceMessageId)
                    .filter(message -> message.getSession().getId().equals(sessionId))
                    .orElseThrow(() -> new IllegalArgumentException("Pocket source message not found: " + sourceMessageId));
            var candidates = extractor.extract(userMessage);
            for (var cast : casts.findBySessionIdAndActiveTrueOrderByEnteredAtAscIdAsc(sessionId)) {
                writer.saveUserSpeech(session, source, cast.getCharacter(), userMessage, candidates, at);
            }
        } catch (RuntimeException ex) {
            // Pocket chat is already durable; memory capture must never turn it into a failed/retried turn.
            log.warn("Pocket memory capture failed for session {} ({})",
                    sessionId,
                    ex.getClass().getSimpleName(),
                    ex);
        }
    }
}
