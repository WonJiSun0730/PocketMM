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
import com.example.projectmm.domain.state.CustomStateValueRepository;
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
    private final CustomStateValueRepository customStateValueRepository;

    @Transactional(readOnly = true)
    public DialogueContext build(
            Long characterId,
            Long conversationId,
            ContentMode requestedMode
    ) {
        return build(characterId, conversationId, requestedMode, "");
    }

    @Transactional(readOnly = true)
    public DialogueContext build(
            Long characterId,
            Long conversationId,
            ContentMode requestedMode,
            String currentMessage
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

        // 다른 캐릭터의 방 이력/구조화 기억을 함께 전달하지 않는다.
        if (!conversation.getCharacter().getId().equals(characterId))
            throw new IllegalArgumentException("Conversation character mismatch");

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
                        conversation.getId(),
                        conversation.getSimulatedAt(),
                        com.example.projectmm.application.memory.SpokenText.from(currentMessage),
                        !conversation.isIndependentStart()
                );

        List<DialogueRelationshipContext> relationships =
                conversation.isIndependentStart() ? List.of() : buildRelationships(characterId);

        return new DialogueContext(
                state.getCharacter().getId(),
                state.getCharacter().getName(),
                dialogueProfile,
                conversation.getEnergy() != null ? conversation.getEnergy() : state.getEnergy(),
                conversation.getMood() != null ? conversation.getMood() : state.getMood(),
                conversation.getSimulatedAt(),
                resolvedMode,
                recentMessages,
                memories,
                relationships,
                conversation.getPlayerProfile() == null ? null : new DialoguePlayerProfile(
                        conversation.getPlayerProfile().getName(), conversation.getPlayerProfile().getDescription()),
                conversation.getPlayerEnergy(),
                conversation.getPlayerMood(),
                buildCustomStates(conversation)
        );
    }

    private List<DialogueCustomState> buildCustomStates(Conversation conversation) {
        // 현재 선택된 플레이어 프로필의 저장된 확정값만 읽는다.
        // 누락된 Value를 기본값으로 생성하거나 보충하지 않는다.
        if (conversation.getPlayerProfile() == null) {
            return List.of();
        }

        return customStateValueRepository
                .findByConversationIdAndDefinitionPlayerProfileIdOrderByIdAsc(
                        conversation.getId(),
                        conversation.getPlayerProfile().getId()
                )
                .stream()
                .map(value -> {
                    var definition = value.getDefinition();
                    return new DialogueCustomState(
                            definition.getId(),
                            definition.getName(),
                            value.getCurrentValue(),
                            definition.getMinValue(),
                            definition.getMaxValue(),
                            definition.getUnit(),
                            definition.getDescription()
                    );
                })
                .toList();
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
            Long conversationId,
            LocalDateTime currentTime,
            String query,
            boolean includeSharedMemories
    ) {
        List<EffectiveMemory> recalledMemories =
                memoryRecallService.recallForConversation(
                        characterId,
                        conversationId,
                        currentTime,
                        MEMORY_LIMIT,
                        query,
                        includeSharedMemories
                );

        return recalledMemories.stream()
                .map(this::toDialogueMemoryContext)
                .toList();
    }

    private DialogueMemoryContext toDialogueMemoryContext(
            EffectiveMemory memory
    ) {
        return switch (memory.accessLevel()) {

            case EXPLICIT ->
                    new DialogueMemoryContext(
                            memory.summary(),
                            memory.importance(),
                            memory.effectiveStrength(),
                            memory.rememberedAt(),
                            memory.evidenceQuote(),
                            memory.fact()
                    );

            case IMPLICIT ->
                    new DialogueMemoryContext(
                            "구체적인 이유나 사건은 기억나지 않지만, "
                                    + "이 대상이나 상황에 익숙함을 느낀다.",
                            memory.importance(),
                            memory.effectiveStrength(),
                            memory.rememberedAt(),
                            null,
                            null
                    );

            case RESIDUAL ->
                    new DialogueMemoryContext(
                            "관련된 구체적인 기억은 떠오르지 않지만, "
                                    + "이유를 특정할 수 없는 감정적 잔향이 남아 있다.",
                            memory.importance(),
                            memory.effectiveStrength(),
                            memory.rememberedAt(),
                            null,
                            null
                    );

            case BLOCKED ->
                    throw new IllegalStateException(
                            "BLOCKED memory must not reach dialogue context"
                    );
        };
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
