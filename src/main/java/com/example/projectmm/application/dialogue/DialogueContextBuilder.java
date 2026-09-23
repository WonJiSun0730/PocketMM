package com.example.projectmm.application.dialogue;

import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.domain.character.*;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatMessageRepository;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DialogueContextBuilder {

    private static final int MEMORY_LIMIT = 5;

    private final CharacterStateRepository characterStateRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemoryRecallService memoryRecallService;
    private final RelationshipRepository relationshipRepository;
    private final ContentModePolicy contentModePolicy;
    private final ConversationRepository conversationRepository;
    private final CharacterExampleRepository characterExampleRepository;

    @Transactional(readOnly = true)
    public DialogueContext build(
            Long characterId,
            Long conversationId,
            ContentMode requestedMode
    ) {
        CharacterState state =
                characterStateRepository
                        .findByCharacterId(characterId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Character state not found. characterId="
                                                + characterId
                                )
                        );

        Conversation conversation =
                conversationRepository
                        .findById(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found. conversationId="
                                                + conversationId
                                )
                        );

        CharacterProfile profile =
                characterProfileRepository
                        .findByCharacterId(characterId)
                        .orElse(null);

        DialogueCharacterProfile dialogueProfile =
                profile == null
                        ? null
                        : new DialogueCharacterProfile(
                        profile.getDescription(),
                        profile.getRawDescription(),
                        buildExamples(
                                characterId,
                                profile.getExampleDialogue()
                        ),
                        profile.getPersonality(),
                        profile.getSpeechStyle(),
                        profile.getBackground(),
                        profile.getAppearance()
                );

        ContentMode resolvedMode =
                contentModePolicy.resolve(requestedMode);

        List<DialogueMessageContext> recentMessages =
                buildRecentMessages(conversationId);

        List<DialogueMemoryContext> memories =
                buildMemories(
                        characterId,
                        conversation.getSimulatedAt()
                );

        List<DialogueRelationshipContext> relationships =
                buildRelationships(characterId);

        return new DialogueContext(
                state.getCharacter().getId(),
                state.getCharacter().getName(),
                dialogueProfile,
                state.getEnergy(),
                state.getMood(),
                conversation.getSimulatedAt(),
                resolvedMode,
                recentMessages,
                memories,
                relationships
        );
    }

    private List<DialogueMessageContext> buildRecentMessages(
            Long conversationId
    ) {
        List<ChatMessage> messages =
                new ArrayList<>(
                        chatMessageRepository
                                .findTop20ByConversationIdOrderByCreatedAtDesc(
                                        conversationId
                                )
                );

        Collections.reverse(messages);

        return messages.stream()
                .map(message ->
                        new DialogueMessageContext(
                                message.getRole(),
                                message.getContent(),
                                message.getCreatedAt()
                        )
                )
                .toList();
    }

    private List<DialogueMemoryContext> buildMemories(
            Long characterId,
            LocalDateTime currentTime
    ) {
        List<EffectiveMemory> recalledMemories =
                memoryRecallService.recall(
                        characterId,
                        currentTime,
                        MEMORY_LIMIT
                );

        return recalledMemories.stream()
                .map(memory ->
                        new DialogueMemoryContext(
                                memory.summary(),
                                memory.importance(),
                                memory.effectiveStrength(),
                                memory.rememberedAt()
                        )
                )
                .toList();
    }

    private List<DialogueRelationshipContext> buildRelationships(
            Long characterId
    ) {
        List<Relationship> relationships =
                relationshipRepository
                        .findByFromCharacterId(characterId);

        return relationships.stream()
                .map(relationship ->
                        new DialogueRelationshipContext(
                                relationship
                                        .getToCharacter()
                                        .getId(),
                                relationship
                                        .getToCharacter()
                                        .getName(),
                                relationship.getAffinity(),
                                relationship.getTrust(),
                                relationship.getTension()
                        )
                )
                .toList();
    }

    private List<String> buildExamples(
            Long characterId,
            String legacyExampleDialogue
    ) {
        List<String> examples =
                characterExampleRepository
                        .findByCharacterIdOrderBySortOrderAsc(characterId)
                        .stream()
                        .map(CharacterExample::getContent)
                        .toList();

        if (!examples.isEmpty()) {
            return examples;
        }

        if (legacyExampleDialogue == null
                || legacyExampleDialogue.isBlank()) {
            return List.of();
        }

        return List.of(
                legacyExampleDialogue.trim()
        );
    }
}