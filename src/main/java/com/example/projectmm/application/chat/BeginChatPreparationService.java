package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeginChatPreparationService {

    private final ConversationRepository conversations;
    private final PresenceClock clock;
    private final EntityManager em;

    public record Preparation(
            Long characterId,
            Long conversationId,
            String claimToken,
            LocalDateTime previousPresence,
            LocalDateTime requestedAt,
            long absenceSeconds,
            boolean autoProgressEnabled,
            String progressPauseReason,
            LocalDateTime simulatedAt,
            Integer roomEnergy,
            Integer roomMood
    ) {

        public boolean catchUpRequired() {
            return autoProgressEnabled
                    && progressPauseReason == null
                    && previousPresence != null
                    && absenceSeconds >= 60;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparation prepare(
            Long characterId,
            Long conversationId
    ) {
        Conversation room =
                conversations.lockForMemory(conversationId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND
                                )
                        );

        em.refresh(room);

        if (!room.getCharacter().getId().equals(characterId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND
            );
        }

        LocalDateTime now =
                clock.now();

        if (room.chatInProgress(now)
                || room.catchUpInProgress(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "대화 또는 자동 진행을 처리 중입니다. 잠시 후 다시 시도해 주세요."
            );
        }

        if (room.getPlayerProfile() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "유저 프로필을 먼저 선택해 주세요."
            );
        }

        LocalDateTime previousPresence =
                room.getLastPresenceAt();

        long absenceSeconds =
                previousPresence == null
                        ? 0
                        : Math.max(
                        0,
                        Duration.between(
                                previousPresence,
                                now
                        ).getSeconds()
                );

        String claimToken =
                UUID.randomUUID().toString();

        room.beginCatchUp(
                claimToken,
                now
        );

        return new Preparation(
                characterId,
                conversationId,
                claimToken,
                previousPresence,
                now,
                absenceSeconds,
                room.autoProgressEnabled(),
                room.getProgressPauseReason(),
                room.getSimulatedAt(),
                room.getEnergy(),
                room.getMood()
        );
    }
}