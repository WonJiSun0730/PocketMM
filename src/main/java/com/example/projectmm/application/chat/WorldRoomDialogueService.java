package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldRoomMessage;
import com.example.projectmm.domain.chat.WorldRoomMessageRepository;
import com.example.projectmm.domain.chat.WorldRoomMessageVariant;
import com.example.projectmm.domain.chat.WorldRoomMessageVariantRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.domain.chat.WorldRoomVariantCastSnapshotRepository;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldRoomDialogueService {

    private final WorldRoomContextBuilder contextBuilder;
    private final DialogueGateway dialogueGateway;
    private final WorldRoomSessionService sessionService;
    private final WorldRoomSessionRepository sessions;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final WorldRoomVariantCastSnapshotRepository castSnapshots;

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
        var beforeCast = sessionService.captureCastState(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, userMessage);
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        var afterCast = sessionService.captureCastState(sessionId);

        var characterMessage = messages.save(new WorldRoomMessage(
                session,
                ChatRole.CHARACTER,
                generated.reply(),
                now()
        ));
        var variant = variants.save(new WorldRoomMessageVariant(
                characterMessage,
                1,
                generated.reply(),
                now()
        ));
        sessionService.saveCastSnapshot(variant, beforeCast, afterCast);

        return new WorldRoomTurn(generated.reply(), applied, appliedSystems);
    }

    @Transactional
    public WorldRoomMessage editMessage(Long sessionId, Long messageId, String content) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        var message = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room message not found. messageId=" + messageId
                ));
        message.edit(content, now());
        return message;
    }

    @Transactional
    public void deleteMessageAndFollowing(Long sessionId, Long messageId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room message not found. messageId=" + messageId
                ));
        messages.findTopBySessionIdAndIdGreaterThanEqualAndRoleOrderByIdAsc(
                        sessionId,
                        messageId,
                        ChatRole.CHARACTER
                )
                .ifPresent(characterMessage -> sessionService.restoreCastBeforeVariant(
                        sessionId,
                        selectedVariant(characterMessage)
                ));
        deleteTimelineFrom(sessionId, messageId);
    }

    @Transactional
    public WorldRoomTurn reroll(Long sessionId, Long messageId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        var target = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .filter(item -> item.getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reroll target must be a character message. messageId=" + messageId
                ));
        var userMessage = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(sessionId, target.getId())
                .filter(item -> item.getRole() == ChatRole.USER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User message for reroll not found. messageId=" + messageId
                ));

        var previousVariant = target.getContent();
        sessionService.restoreCastBeforeVariant(sessionId, selectedVariant(target));
        var context = contextBuilder.buildSession(sessionId);
        var rerollPrompt = userMessage.getContent()
                + "\n\n[Previous response variant]\n"
                + previousVariant
                + "\n\nGenerate another natural variant of this response without copying it.";
        var beforeCast = sessionService.captureCastState(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, rerollPrompt);
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        var afterCast = sessionService.captureCastState(sessionId);

        int variantNo = Math.toIntExact(variants.countByMessageId(messageId)) + 1;
        var variant = variants.save(new WorldRoomMessageVariant(
                target,
                variantNo,
                generated.reply(),
                now()
        ));
        sessionService.saveCastSnapshot(variant, beforeCast, afterCast);
        target.selectVariant(variantNo, generated.reply());

        return new WorldRoomTurn(generated.reply(), applied);
    }

    @Transactional
    public WorldRoomTurn regenerate(Long sessionId, Long messageId) {
        var session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        var target = messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .filter(item -> item.getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Regeneration target must be a character message. messageId=" + messageId
                ));
        var userMessage = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(sessionId, target.getId())
                .filter(item -> item.getRole() == ChatRole.USER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User message for regeneration not found. messageId=" + messageId
                ));

        sessionService.restoreCastBeforeVariant(sessionId, selectedVariant(target));
        deleteTimelineFrom(sessionId, target.getId());
        messages.flush();

        var context = contextBuilder.buildSession(sessionId);
        var beforeCast = sessionService.captureCastState(sessionId);
        var generated = dialogueGateway.generateWorldRoomTurn(context, userMessage.getContent());
        var applied = sessionService.applyCastChange(sessionId, generated.castChange());
        var appliedSystems = sessionService.applySystemChanges(sessionId, generated.systemChanges());
        var afterCast = sessionService.captureCastState(sessionId);

        var characterMessage = messages.save(new WorldRoomMessage(
                session,
                ChatRole.CHARACTER,
                generated.reply(),
                now()
        ));
        var variant = variants.save(new WorldRoomMessageVariant(
                characterMessage,
                1,
                generated.reply(),
                now()
        ));
        sessionService.saveCastSnapshot(variant, beforeCast, afterCast);

        return new WorldRoomTurn(generated.reply(), applied);
    }

    @Transactional(readOnly = true)
    public List<WorldRoomMessageVariant> variants(Long sessionId, Long messageId) {
        var message = requireCharacterMessage(sessionId, messageId);
        return variants.findByMessageIdOrderByVariantNoAsc(message.getId());
    }

    @Transactional
    public WorldRoomMessage selectVariant(Long sessionId, Long messageId, int variantNo) {
        var message = requireCharacterMessage(sessionId, messageId);
        var variant = variants.findByMessageIdAndVariantNo(messageId, variantNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room message variant not found. messageId=" + messageId
                                + ", variantNo=" + variantNo
                ));
        sessionService.restoreCastAfterVariant(sessionId, variant);
        message.selectVariant(variant.getVariantNo(), variant.getContent());
        return message;
    }

    @Transactional(readOnly = true)
    public List<WorldRoomMessage> history(Long sessionId, Long beforeId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));

        var page = beforeId == null
                ? messages.findTop50BySessionIdOrderByIdDesc(sessionId)
                : messages.findTop50BySessionIdAndIdLessThanOrderByIdDesc(sessionId, beforeId);

        return page.stream()
                .sorted(Comparator.comparing(WorldRoomMessage::getId))
                .toList();
    }

    private void deleteTimelineFrom(Long sessionId, Long messageId) {
        castSnapshots.deleteByVariantMessageSessionIdAndVariantMessageIdGreaterThanEqual(
                sessionId,
                messageId
        );
        variants.deleteByMessageSessionIdAndMessageIdGreaterThanEqual(sessionId, messageId);
        messages.deleteBySessionIdAndIdGreaterThanEqual(sessionId, messageId);
    }

    private WorldRoomMessageVariant selectedVariant(WorldRoomMessage message) {
        var selectedVariantNo = message.getSelectedVariantNo();
        if (selectedVariantNo == null) {
            throw new IllegalStateException(
                    "Selected variant not found. messageId=" + message.getId()
            );
        }
        return variants.findByMessageIdAndVariantNo(message.getId(), selectedVariantNo)
                .orElseThrow(() -> new IllegalStateException(
                        "Selected variant not found. messageId=" + message.getId()
                                + ", variantNo=" + selectedVariantNo
                ));
    }

    private WorldRoomMessage requireCharacterMessage(Long sessionId, Long messageId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "World room session not found. sessionId=" + sessionId
                ));
        return messages.findById(messageId)
                .filter(item -> item.getSession().getId().equals(sessionId))
                .filter(item -> item.getRole() == ChatRole.CHARACTER)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Character message not found. messageId=" + messageId
                ));
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
