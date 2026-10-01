package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldRoomMessage;
import com.example.projectmm.domain.chat.WorldRoomMessageRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class WorldRoomDialogueService {

    private final WorldRoomContextBuilder contextBuilder;
    private final DialogueGateway dialogueGateway;
    private final WorldRoomSessionService sessionService;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomMessageRepository messages;

    @Transactional
    public WorldRoomTurn chat(Long sessionId, String userMessage) {
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));

        messages.save(new WorldRoomMessage(
                session,
                ChatRole.USER,
                userMessage,
                now()
        ));

        var context = contextBuilder.buildSession(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, userMessage);
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());

        messages.save(new WorldRoomMessage(
                session,
                ChatRole.CHARACTER,
                generated.reply(),
                now()
        ));

        return new WorldRoomTurn(generated.reply(), applied);
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
