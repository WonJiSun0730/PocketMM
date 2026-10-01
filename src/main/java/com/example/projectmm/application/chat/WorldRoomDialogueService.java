package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorldRoomDialogueService {

    private final WorldRoomContextBuilder contextBuilder;
    private final DialogueGateway dialogueGateway;
    private final WorldRoomService roomService;

    public WorldRoomTurn chat(Long roomId, String userMessage) {
        var context = contextBuilder.build(roomId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, userMessage);
        var applied = roomService.applyCastChange(roomId, generated.castChange());
        return new WorldRoomTurn(generated.reply(), applied);
    }
}
