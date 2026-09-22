package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueContextBuilder;
import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatMessageRepository;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.dialogue.DialogueContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final CharacterRepository characterRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DialogueContextBuilder dialogueContextBuilder;
    private final DialogueGateway dialogueGateway;
    private final ContentModePolicy contentModePolicy;

    public ChatResponse chat(
            Long characterId,
            ChatRequest request
    ) {
        if (request == null
                || request.message() == null
                || request.message().isBlank()) {

            throw new IllegalArgumentException(
                    "message must not be blank"
            );
        }

        Character character =
                characterRepository
                        .findById(characterId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Character not found. characterId="
                                                + characterId
                                )
                        );

        ContentMode resolvedMode =
                contentModePolicy.resolve(
                        request.contentMode()
                );

        DialogueContext context =
                dialogueContextBuilder.build(
                        characterId,
                        resolvedMode
                );

        String generatedMessage =
                dialogueGateway.generate(
                        context,
                        request.message()
                );

        LocalDateTime userMessageTime =
                LocalDateTime.now();

        ChatMessage userMessage =
                new ChatMessage(
                        character,
                        ChatRole.USER,
                        request.message(),
                        resolvedMode,
                        userMessageTime
                );

        chatMessageRepository.save(userMessage);

        LocalDateTime characterMessageTime =
                LocalDateTime.now();

        ChatMessage characterMessage =
                new ChatMessage(
                        character,
                        ChatRole.CHARACTER,
                        generatedMessage,
                        resolvedMode,
                        characterMessageTime
                );

        chatMessageRepository.save(characterMessage);

        return new ChatResponse(
                character.getId(),
                character.getName(),
                generatedMessage,
                resolvedMode,
                characterMessageTime
        );
    }

    public List<ChatHistoryResponse> getRecentMessages(
            Long characterId
    ) {
        if (!characterRepository.existsById(characterId)) {
            throw new IllegalArgumentException(
                    "Character not found. characterId="
                            + characterId
            );
        }

        List<ChatMessage> messages =
                new ArrayList<>(
                        chatMessageRepository
                                .findTop20ByCharacterIdOrderByCreatedAtDesc(
                                        characterId
                                )
                );

        Collections.reverse(messages);

        return messages.stream()
                .map(message ->
                        new ChatHistoryResponse(
                                message.getId(),
                                message.getRole(),
                                message.getContent(),
                                message.getContentMode(),
                                message.getCreatedAt()
                        )
                )
                .toList();
    }
}