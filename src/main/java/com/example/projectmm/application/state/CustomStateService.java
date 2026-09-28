package com.example.projectmm.application.state;

import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.state.CustomStateDefinitionRepository;
import com.example.projectmm.domain.state.CustomStateValue;
import com.example.projectmm.domain.state.CustomStateValueRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomStateService {
    private final ConversationRepository conversations;
    private final CustomStateDefinitionRepository definitions;
    private final CustomStateValueRepository values;
    private final EntityManager em;

    @Transactional
    public void initialize(Long conversationId) {
        var room = locked(conversationId);
        requireProfile(room);
        for (var definition : definitions.findByPlayerProfileIdOrderByIdAsc(room.getPlayerProfile().getId())) {
            if (values.findByConversationIdAndDefinitionId(conversationId, definition.getId()).isEmpty()) {
                values.save(new CustomStateValue(room, definition));
            }
        }
    }

    @Transactional
    public void initializeSelectedProfile(Conversation room) {
        if (room == null || room.getId() == null) {
            throw new IllegalArgumentException("Conversation is required.");
        }

        requireProfile(room);

        for (var definition :
                definitions.findByPlayerProfileIdOrderByIdAsc(
                        room.getPlayerProfile().getId()
                )) {

            if (values.findByConversationIdAndDefinitionId(
                    room.getId(),
                    definition.getId()
            ).isEmpty()) {
                values.save(new CustomStateValue(room, definition));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CustomStateSnapshot> current(Long conversationId) {
        var room = conversations.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (room.getPlayerProfile() == null) {
            return List.of();
        }

        return values
                .findByConversationIdAndDefinitionPlayerProfileIdOrderByIdAsc(
                        conversationId,
                        room.getPlayerProfile().getId()
                )
                .stream()
                .map(CustomStateSnapshot::from)
                .toList();
    }
    @Transactional
    public CustomStateSnapshot updateByUser(Long conversationId, Long definitionId, double currentValue) {
        var room = locked(conversationId);
        requireProfile(room);
        var definition = definitions.findById(definitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!definition.getPlayerProfile().getId().equals(room.getPlayerProfile().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택된 프로필의 상태 정의가 아닙니다.");
        }
        var value = values.findByConversationIdAndDefinitionId(conversationId, definitionId)
                .orElseGet(() -> new CustomStateValue(room, definition));
        // 도메인 검증에 성공한 값만 저장하며 실패한 요청은 트랜잭션을 롤백한다.
        value.updateCurrentValue(currentValue);
        return CustomStateSnapshot.from(values.save(value));
    }

    private Conversation locked(Long conversationId) {
        // 프로필 선택과 같은 방 잠금을 사용해 초기화/수정의 중복 생성과 경합을 막는다.
        var room = conversations.lockForMemory(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        em.refresh(room);
        return room;
    }

    private void requireProfile(Conversation room) {
        if (room.getPlayerProfile() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "유저 프로필을 먼저 선택해 주세요.");
        }
    }
}
