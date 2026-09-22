package com.example.projectmm.application.dialogue;

import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.chat.ChatMessageRepository;
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

    @Transactional(readOnly = true)
    public DialogueContext build(
            Long characterId,
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
                        profile.getExampleDialogue(),
                        profile.getPersonality(),
                        profile.getSpeechStyle(),
                        profile.getBackground(),
                        profile.getAppearance()
                );

        ContentMode resolvedMode =
                contentModePolicy.resolve(requestedMode);

        List<DialogueMessageContext> recentMessages =
                buildRecentMessages(characterId);

        List<DialogueMemoryContext> memories =
                buildMemories(
                        characterId,
                        state.getSimulatedAt()
                );

        List<DialogueRelationshipContext> relationships =
                buildRelationships(characterId);

        return new DialogueContext(
                state.getCharacter().getId(),
                state.getCharacter().getName(),
                dialogueProfile,
                state.getEnergy(),
                state.getMood(),
                state.getSimulatedAt(),
                resolvedMode,
                recentMessages,
                memories,
                relationships
        );
    }

    private List<DialogueMessageContext> buildRecentMessages(
            Long characterId
    ) {
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
}