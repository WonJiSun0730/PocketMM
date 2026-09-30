package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatchUpClaimService {

    private final ConversationRepository conversations;
    private final EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String claim(
            Long characterId,
            Long conversationId,
            LocalDateTime now
    ) {
        Conversation room =
                conversations.lockForMemory(conversationId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND)
                        );

        em.refresh(room);

        if (!room.getCharacter().getId().equals(characterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (room.chatInProgress(now)
                || room.catchUpInProgress(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "대화 또는 자동 진행을 처리 중입니다. 잠시 후 다시 시도해 주세요."
            );
        }

        String token = UUID.randomUUID().toString();

        room.beginCatchUp(token, now);

        return token;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(
            Long characterId,
            Long conversationId,
            String token
    ) {
        Conversation room =
                conversations.lockForMemory(conversationId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND)
                        );

        em.refresh(room);

        if (!room.getCharacter().getId().equals(characterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        room.endCatchUp(token);
    }
}