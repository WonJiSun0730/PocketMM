package com.example.projectmm.application.dialogue;

import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.dialogue.DialogueTurn;
import com.example.projectmm.domain.dialogue.DialogueStateChange;
import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;

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
        return generateWorldRoomTurn(context, userMessage).reply();
    }

    default WorldRoomTurn analyzeEditedWorldRoomState(
            WorldRoomDialogueContext context,
            String editedReply
    ) {
        return generateWorldRoomTurn(context, """
                [STATE EXTRACTION ONLY — DO NOT CONTINUE THE STORY]
                Analyze the following edited assistant reply as an already-completed scene.
                Return the required structured JSON envelope. Set reply to an empty placeholder
                or short acknowledgement; it will be discarded and never shown to the player.
                Extract only explicit final location, player condition and inventory changes
                that actually occur in the edited passage. No speculative changes.
                Set elapsedSeconds to 0, enterCharacterIds/leaveCharacterIds and
                systemChanges to empty arrays. Do not narrate a new event.

                [EDITED ASSISTANT REPLY]
                """ + editedReply);
    }

    default WorldRoomTurn generateWorldRoomTurn(
            WorldRoomDialogueContext context,
            String userMessage
    ) {
        throw new UnsupportedOperationException("World room dialogue is not supported by this gateway");
    }
}

