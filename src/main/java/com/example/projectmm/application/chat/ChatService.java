package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueContextBuilder;
import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.time.TimeChange;
import com.example.projectmm.domain.time.TimeInterpretation;
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
    private final CharacterProfileRepository characterProfileRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DialogueContextBuilder dialogueContextBuilder;
    private final DialogueGateway dialogueGateway;
    private final ContentModePolicy contentModePolicy;
    private final TimeInterpreter timeInterpreter;

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

        Conversation conversation =
                conversationRepository
                        .findTopByCharacterIdOrderByCreatedAtDesc(characterId)
                        .orElseGet(() ->
                                createConversation(character)
                        );

        TimeInterpretation timeInterpretation =
                timeInterpreter.interpret(
                        request.message(),
                        conversation.getSimulatedAt()
                );

        if (timeInterpretation.matched()) {
            TimeChange timeChange =
                    timeInterpretation.timeChange();

            if (timeChange.changed()) {
                conversation.updateSimulatedAt(
                        timeChange.newTime()
                );

                conversationRepository.save(conversation);
            }
        }
        ContentMode resolvedMode =
                contentModePolicy.resolve(
                        request.contentMode()
                );

        DialogueContext context =
                dialogueContextBuilder.build(
                        characterId,
                        conversation.getId(),
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
                        conversation,
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
                        conversation,
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
        Character character =
                characterRepository
                        .findById(characterId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Character not found. characterId="
                                                + characterId
                                )
                        );

        Conversation conversation =
                conversationRepository
                        .findTopByCharacterIdOrderByCreatedAtDesc(characterId)
                        .orElseGet(() ->
                                createConversation(character)
                        );

        if (!chatMessageRepository.existsByConversationId(
                conversation.getId()
        )) {
            characterProfileRepository
                    .findByCharacterId(characterId)
                    .map(CharacterProfile::getIntro)
                    .filter(intro ->
                            intro != null && !intro.isBlank()
                    )
                    .ifPresent(intro ->
                            chatMessageRepository.save(
                                    new ChatMessage(
                                            conversation,
                                            character,
                                            ChatRole.CHARACTER,
                                            intro,
                                            ContentMode.NORMAL,
                                            LocalDateTime.now()
                                    )
                            )
                    );
        }

        List<ChatMessage> messages =
                new ArrayList<>(
                        chatMessageRepository
                                .findTop20ByConversationIdOrderByCreatedAtDesc(
                                        conversation.getId()
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

    private Conversation createConversation(
            Character character
    ) {
        LocalDateTime simulatedAt =
                LocalDateTime.now();

        Conversation conversation =
                new Conversation(
                        character,
                        simulatedAt
                );

        return conversationRepository.save(conversation);
    }
}