package com.example.projectmm.application.state;

import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.state.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomStateProposalService {
    private final ConversationRepository conversations;
    private final CustomStateDefinitionRepository definitions;
    private final CustomStateValueRepository values;
    private final EntityManager em;

    @Transactional
    public List<CustomStateSnapshot> apply(Long conversationId, List<CustomStateChangeProposal> proposals) {
        if (conversationId == null || conversationId <= 0 || proposals == null) {
            throw new IllegalArgumentException("Conversation ID and proposals are required.");
        }
        var ids = new HashSet<Long>();
        for (var proposal : proposals) {
            if (proposal == null) throw new IllegalArgumentException("Proposal must not be null.");
            proposal.validate();
            // 동일 정의의 중복 제안은 순서에 따른 합산/덮어쓰기 모호성을 피하기 위해 거부한다.
            if (!ids.add(proposal.definitionId())) throw new IllegalArgumentException("Duplicate definition proposal.");
        }
        var batch = List.copyOf(proposals);
        // updateByUser 및 프로필 변경과 동일한 잠금으로 방 단위 적용 순서를 직렬화한다.
        var room = conversations.lockForMemory(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        em.refresh(room);
        if (room.getPlayerProfile() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "유저 프로필이 필요합니다.");

        var changes = new ArrayList<ValidatedChange>();
        for (var proposal : batch) {
            var definition = definitions.findById(proposal.definitionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            em.refresh(definition);
            if (!definition.getPlayerProfile().getId().equals(room.getPlayerProfile().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택된 프로필의 상태 정의가 아닙니다.");
            }
            var value = values.findByConversationIdAndDefinitionId(conversationId, definition.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "확정 상태를 먼저 초기화해야 합니다."));
            // 오래된 영속성 컨텍스트도 배제한다. AI/DialogueContext 값은 계산 입력으로 받지 않는다.
            em.refresh(value, LockModeType.PESSIMISTIC_WRITE);
            double next = proposal.resolve(value.getCurrentValue(), definition.getMinValue(), definition.getMaxValue());
            changes.add(new ValidatedChange(value, next));
        }
        // 전부 검증한 뒤에만 엔티티를 변경한다. 중간 조회의 자동 flush로 일부 제안이 저장되지 않게 한다.
        for (var change : changes) change.value().updateCurrentValue(change.next());
        em.flush();
        return changes.stream().map(change -> CustomStateSnapshot.from(change.value())).toList();
    }

    private record ValidatedChange(CustomStateValue value, double next) {}
}
