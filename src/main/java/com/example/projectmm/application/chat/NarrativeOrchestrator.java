package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.Conversation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Narrative runtime의 상위 진입점.
 *
 * 기존 dialogue/catch-up 실행기를 재구현하지 않고,
 * narrative 실행 순서와 현재 실행 모드를 명시적으로 연결한다.
 */
@Service
@RequiredArgsConstructor
public class NarrativeOrchestrator {

    private final ConversationProgressService progressService;

    /**
     * 사용자의 현재 대화를 시작한다.
     *
     * beginChat() 내부에서 필요한 reconnect/catch-up이 먼저 처리된다.
     * 이 메서드가 반환되는 시점의 현재 실행 모드는 항상 LIVE_DIALOGUE다.
     *
     * entryMode는 LIVE 진입 전에 어떤 narrative 처리가 수행됐는지를 나타낸다.
     */
    public NarrativeOperation beginLiveDialogue(
            Long characterId,
            Long conversationId
    ) {
        var chatOperation =
                progressService.beginChat(
                        characterId,
                        conversationId
                );

        return new NarrativeOperation(
                chatOperation.room(),
                chatOperation.token(),
                NarrativeMode.LIVE_DIALOGUE,
                chatOperation.entry()
        );
    }

    /**
     * 현재 LIVE dialogue 실행에 필요한 narrative operation.
     *
     * mode:
     * 현재 실행할 narrative mode.
     *
     * entryMode:
     * 현재 LIVE dialogue에 진입하기 직전에 수행된 mode.
     * catch-up이 없었다면 LIVE_DIALOGUE,
     * catch-up이 있었다면 CATCH_UP이다.
     */
    public record NarrativeOperation(
            Conversation room,
            String token,
            NarrativeMode mode,
            NarrativeEntry entry
    ) {}
}