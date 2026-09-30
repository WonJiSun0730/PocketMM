package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueContextBuilder;
import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.application.memory.ChatMemoryService;
import com.example.projectmm.application.memory.ChatMemoryResult;
import com.example.projectmm.domain.dialogue.DialogueMemoryContext;
import com.example.projectmm.domain.character.Character;
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
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DialogueContextBuilder dialogueContextBuilder;
    private final DialogueGateway dialogueGateway;
    private final ContentModePolicy contentModePolicy;
    private final TimeInterpreter timeInterpreter;
    private final ChatMemoryService chatMemoryService;
    private final ConversationProgressService progressService;
    private final NarrativeOrchestrator narrativeOrchestrator;

    public ChatResponse chatInWorld(Long worldId, Long characterId, ChatRequest request) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found. characterId=" + characterId));
        if (character.getWorld() == null || !character.getWorld().getId().equals(worldId)) {
            throw new IllegalArgumentException("Character is not assigned to this world.");
        }
        Conversation conversation = conversationRepository
                .findTopByCharacterIdAndWorldIdOrderByCreatedAtDescIdDesc(characterId, worldId)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.forWorld(character, character.getWorld(), LocalDateTime.now())
                ));
        var operation = narrativeOrchestrator.beginLiveDialogue(characterId, conversation.getId());
        conversation = operation.room();
        try {
            String message = request == null || request.message() == null ? "" : request.message().trim();
            ChatRequest normalized = new ChatRequest(message, request == null ? null : request.contentMode());
            return generateReply(character, conversation, operation.token(), normalized);
        } finally {
            progressService.endChat(characterId, conversation.getId(), operation.token());
        }
    }

    public Conversation getOrCreateWorldConversation(Long worldId, Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found. characterId=" + characterId));
        if (character.getWorld() == null || !character.getWorld().getId().equals(worldId)) {
            throw new IllegalArgumentException("Character is not assigned to this world.");
        }
        return conversationRepository
                .findTopByCharacterIdAndWorldIdOrderByCreatedAtDescIdDesc(characterId, worldId)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.forWorld(character, character.getWorld(), LocalDateTime.now())
                ));
    }

    public List<ChatHistoryResponse> getRecentWorldMessages(Long worldId, Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found. characterId=" + characterId));
        if (character.getWorld() == null || !character.getWorld().getId().equals(worldId)) {
            throw new IllegalArgumentException("Character is not assigned to this world.");
        }
        Conversation conversation = conversationRepository
                .findTopByCharacterIdAndWorldIdOrderByCreatedAtDescIdDesc(characterId, worldId)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.forWorld(character, character.getWorld(), LocalDateTime.now())
                ));
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversation.getId())
        );
        Collections.reverse(messages);
        return messages.stream().map(message -> new ChatHistoryResponse(
                message.getId(), message.getRole(), message.getContent(), message.getContentMode(), message.getCreatedAt()
        )).toList();
    }

    public ChatResponse chat(
            Long characterId,
            ChatRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "request must not be null"
            );
        }

        String message =
                request.message() == null
                        ? ""
                        : request.message().trim();

        request =
                new ChatRequest(
                        message,
                        request.contentMode()
                );

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
                        .findTopByCharacterIdOrderByCreatedAtDescIdDesc(characterId)
                        .orElseGet(() ->
                                createConversation(character)
                        );

        var operation =
                narrativeOrchestrator.beginLiveDialogue(
                        characterId,
                        conversation.getId()
                );

        conversation = operation.room();
        try {
            return generateReply(character, conversation, operation.token(), request);
        } finally {
            // 외부 AI 호출에는 DB 잠금을 유지하지 않으며 실패 시에도 처리 표시를 해제한다.
            progressService.endChat(characterId, conversation.getId(), operation.token());
        }
    }

    private ChatResponse generateReply(Character character, Conversation conversation, String token, ChatRequest request) {
        Long characterId = character.getId();
        TimeInterpretation timeInterpretation =
                request.message().isBlank()
                        ? TimeInterpretation.noMatch()
                        : timeInterpreter.interpret(
                        request.message(),
                        conversation.getSimulatedAt()
                );

        LocalDateTime previousTime =
                conversation.getSimulatedAt();

        TimeChange timeChange =
                timeInterpretation.matched()
                        ? timeInterpretation.timeChange()
                        : null;

        if (timeChange != null) {

            if (timeChange.changed()) {
                progressService.applyChatTime(characterId, conversation.getId(), token, timeChange.newTime());
                conversation.updateSimulatedAt(timeChange.newTime());
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
                        resolvedMode,
                        request.message()
                );

        LocalDateTime aiContextTime =
                context.simulatedAt();

        var generatedTurn =
                dialogueGateway.generateTurn(
                        context,
                        request.message()
                );

        String generatedMessage = generatedTurn.reply();
        var completed = progressService.completeChat(characterId, conversation.getId(), token,
                request.message(), generatedTurn, resolvedMode);
        ChatMessage userMessage =
                completed.source();

        LocalDateTime characterMessageTime =
                userMessage == null
                        ? LocalDateTime.now()
                        : userMessage.getCreatedAt()
                        .plusNanos(1000);

        ChatMemoryResult memoryResult =
                userMessage == null
                        ? ChatMemoryResult.empty("SKIPPED")
                        : chatMemoryService.remember(
                        conversation,
                        userMessage,
                        aiContextTime
                );

        return new ChatResponse(
                character.getId(),
                character.getName(),
                generatedMessage,
                resolvedMode,
                characterMessageTime,
                new ChatDebugInfo(
                        conversation.getSimulatedAt(),
                        aiContextTime,
                        timeInterpretation.interpreter(),
                        timeInterpretation.precision(),
                        timeInterpretation.durationScale(),
                        timeChange == null ? null : timeChange.expectedElapsedMinutes(),
                        timeChange == null ? 0 : timeChange.appliedElapsedMinutes(),
                        previousTime,
                        conversation.getSimulatedAt(),
                        timeChange == null ? null : timeChange.sourceExpression(),
                        memoryResult.status(),
                        memoryResult.saved(),
                        context.memories().stream().map(DialogueMemoryContext::summary).toList(),
                        completed.stateUpdate(),
                        completed.playerStateUpdate()
                )
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
                        .findTopByCharacterIdOrderByCreatedAtDescIdDesc(characterId)
                        .orElseGet(() ->
                                createConversation(character)
                        );



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
