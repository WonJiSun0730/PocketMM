package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.chat.WorldRoomMessageRepository;
import com.example.projectmm.domain.chat.WorldRoomMessageVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists post-extraction memory state on the reply generated for a specific user message.
 * A missing reply (e.g. after deletion) is harmless.
 */
@Service
@RequiredArgsConstructor
public class PocketTurnTestSnapshotService {
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final PocketMemoryInspectorService inspector;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void captureAfterExtraction(Long sessionId, Long userMessageId) {
        var reply = messages.findTopBySessionIdAndIdGreaterThanEqualAndRoleOrderByIdAsc(
                sessionId, userMessageId + 1, ChatRole.CHARACTER);
        if (reply.isEmpty()) return;
        var message = reply.get();
        int selectedNo = message.getSelectedVariantNo() == null ? 1 : message.getSelectedVariantNo();
        variants.findByMessageIdAndVariantNo(message.getId(), selectedNo).ifPresent(variant ->
                variant.captureTestMemoryAfter(mapper.writeValueAsString(inspector.snapshotAll(sessionId))));
    }
}
