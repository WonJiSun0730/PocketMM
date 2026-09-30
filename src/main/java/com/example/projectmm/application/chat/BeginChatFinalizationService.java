package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.simulation.CatchUpExecutionService;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatMessageRepository;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationCatchUp;
import com.example.projectmm.domain.chat.ConversationCatchUpRepository;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeginChatFinalizationService {

    private final ConversationRepository conversations;
    private final ConversationCatchUpRepository reports;
    private final ChatMessageRepository messages;
    private final MemoryRepository memories;
    private final CatchUpExecutionService catchUpExecution;
    private final EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationProgressService.ChatOperation finalizeDirect(
            BeginChatPreparationService.Preparation preparation
    ) {
        Conversation room =
                conversations.lockForMemory(
                        preparation.conversationId()
                ).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND)
                );

        em.refresh(room);

        if (!room.getCharacter().getId().equals(
                preparation.characterId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND
            );
        }

        if (!preparation.claimToken().equals(
                room.getCatchUpOperationId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "자동 진행 처리 권한이 만료되었거나 변경되었습니다."
            );
        }

        room.markPresent(
                preparation.requestedAt()
        );

        room.endCatchUp(
                preparation.claimToken()
        );

        String chatToken =
                UUID.randomUUID().toString();

        room.beginChat(
                chatToken,
                preparation.requestedAt()
        );

        return new ConversationProgressService.ChatOperation(
                room,
                chatToken,
                NarrativeEntry.DIRECT
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationProgressService.ChatOperation finalizeBeginChat(
            BeginChatPreparationService.Preparation preparation,
            BeginChatCatchUpResolver.Resolution resolution
    ) {
        Conversation room =
                conversations.lockForMemory(
                        preparation.conversationId()
                ).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND)
                );

        em.refresh(room);

        if (!room.getCharacter().getId().equals(
                preparation.characterId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND
            );
        }

        if (!preparation.claimToken().equals(
                room.getCatchUpOperationId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "자동 진행 처리 권한이 만료되었거나 변경되었습니다."
            );
        }

        var actions =
                new ArrayList<String>();

        for (var result : resolution.aiResults()) {
            var events =
                    catchUpExecution.persist(
                            resolution.state(),
                            result,
                            room
                    );

            for (var event : events) {
                String summary =
                        room.getCharacter().getName()
                                + " — "
                                + event.getSummary();

                messages.save(
                        new ChatMessage(
                                room,
                                room.getCharacter(),
                                ChatRole.CHARACTER,
                                "*" + summary + "*",
                                ContentMode.NORMAL,
                                preparation.requestedAt()
                        )
                );

                actions.add(
                        summary
                );
            }
        }

        for (var candidate : resolution.restCandidates()) {
            String summary =
                    room.getCharacter().getName()
                            + " — 피로가 누적되어 휴식을 취했다.";

            var source =
                    messages.save(
                            new ChatMessage(
                                    room,
                                    room.getCharacter(),
                                    ChatRole.CHARACTER,
                                    "*" + summary + "*",
                                    ContentMode.NORMAL,
                                    preparation.requestedAt()
                            )
                    );

            memories.save(
                    Memory.fromAutomaticAction(
                            room,
                            source,
                            summary,
                            candidate.importance(),
                            candidate.occurredAt()
                    )
            );

            actions.add(
                    summary
            );
        }

        room.applyRoomState(
                resolution.state().getEnergy(),
                resolution.state().getMood(),
                resolution.state().getSimulatedAt()
        );

        room.pauseProgress(
                resolution.pauseReason()
        );

        reports.save(
                new ConversationCatchUp(
                        room,
                        preparation.requestedAt(),
                        preparation.simulatedAt(),
                        resolution.state().getSimulatedAt(),
                        preparation.absenceSeconds(),
                        resolution.capped(),
                        resolution.pauseReason(),
                        String.join("\n", actions)
                )
        );

        /*
         * TX1에서는 presence를 소비하지 않았다.
         * 모든 catch-up persistence가 성공한 지금에만
         * 고정해 둔 요청 시각까지 처리 완료로 표시한다.
         */
        room.markPresent(
                preparation.requestedAt()
        );

        room.endCatchUp(
                preparation.claimToken()
        );

        String chatToken =
                UUID.randomUUID().toString();

        room.beginChat(
                chatToken,
                preparation.requestedAt()
        );

        return new ConversationProgressService.ChatOperation(
                room,
                chatToken,
                NarrativeEntry.AFTER_CATCH_UP
        );
    }
}