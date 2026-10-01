package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.dialogue.DialogueTurn;
import com.example.projectmm.domain.dialogue.DialogueStateChange;
import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;

public interface DialogueGateway {

    String generate(
            DialogueContext context,
            String userMessage
    );

    // 텍스트 전용 구현은 기존 동작을 유지하고, 지원하는 구현만 상태 제안을 반환한다.
    default DialogueTurn generateTurn(DialogueContext context, String userMessage) {
        return new DialogueTurn(generate(context, userMessage), DialogueStateChange.none());
    }
    default String generateWorldRoom(
            WorldRoomDialogueContext context,
            String userMessage
    ) {
        throw new UnsupportedOperationException("World room dialogue is not supported by this gateway");
    }
}

