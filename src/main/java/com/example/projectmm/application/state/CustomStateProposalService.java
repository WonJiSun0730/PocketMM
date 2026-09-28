package com.example.projectmm.application.state;

import com.example.projectmm.domain.chat.ConversationRepository;
import com.example.projectmm.domain.state.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
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

    public enum ApplyStatus { APPLIED, NONE, REJECTED }

    @Transactional
    public List<CustomStateSnapshot> apply(Long conversationId, List<CustomStateChangeProposal> proposals) {
        try {
            return mutate(validate(conversationId, proposals));
        } catch (ProposalRejected rejected) {
            // 5A의 직접 호출 계약은 유지한다. 채팅 경로만 거절을 결과값으로 반환한다.
            throw rejected.reason;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ApplyStatus applyForChat(Long conversationId, List<CustomStateChangeProposal> proposals) {
        if (proposals != null && proposals.isEmpty()) return ApplyStatus.NONE;
        List<ValidatedChange> changes;
        try {
            changes = validate(conversationId, proposals);
        } catch (ProposalRejected rejected) {
            // 변경 전의 순수 검증 거절만 흡수한다. 예외가 프록시 밖으로 나가지 않아 rollback-only가 되지 않는다.
            return ApplyStatus.REJECTED;
        }
        // DB/잠금/flush 장애는 흡수하지 않는다. 메시지 및 NPC 상태와 함께 전체 롤백해야 한다.
        mutate(changes);
        return ApplyStatus.APPLIED;
    }

    private List<ValidatedChange> validate(Long conversationId, List<CustomStateChangeProposal> proposals) {
        if (conversationId == null || conversationId <= 0 || proposals == null) {
            throw new ProposalRejected(new IllegalArgumentException("Conversation ID and proposals are required."));
        }
        var ids = new HashSet<Long>();
        for (var proposal : proposals) {
            if (proposal == null) throw new ProposalRejected(new IllegalArgumentException("Proposal must not be null."));
            try {
                proposal.validate();
            } catch (IllegalArgumentException invalid) {
                throw new ProposalRejected(invalid);
            }
            // 동일 정의의 중복 제안은 순서에 따른 합산/덮어쓰기 모호성을 피하기 위해 거부한다.
            if (!ids.add(proposal.definitionId())) throw new ProposalRejected(new IllegalArgumentException("Duplicate definition proposal."));
        }
        var batch = List.copyOf(proposals);
        // updateByUser 및 프로필 변경과 동일한 잠금으로 방 단위 적용 순서를 직렬화한다.
        var room = conversations.lockForMemory(conversationId)
                .orElseThrow(() -> new ProposalRejected(new ResponseStatusException(HttpStatus.NOT_FOUND)));
        em.refresh(room);
        if (room.getPlayerProfile() == null) throw new ProposalRejected(new ResponseStatusException(HttpStatus.CONFLICT, "유저 프로필이 필요합니다."));

        var changes = new ArrayList<ValidatedChange>();
        for (var proposal : batch) {
            var definition = definitions.findById(proposal.definitionId())
                    .orElseThrow(() -> new ProposalRejected(new ResponseStatusException(HttpStatus.NOT_FOUND)));
            em.refresh(definition);
            if (!definition.getPlayerProfile().getId().equals(room.getPlayerProfile().getId())) {
                throw new ProposalRejected(new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택된 프로필의 상태 정의가 아닙니다."));
            }
            var value = values.findByConversationIdAndDefinitionId(conversationId, definition.getId())
                    .orElseThrow(() -> new ProposalRejected(new ResponseStatusException(HttpStatus.CONFLICT, "확정 상태를 먼저 초기화해야 합니다.")));
            // 오래된 영속성 컨텍스트도 배제한다. AI/DialogueContext 값은 계산 입력으로 받지 않는다.
            em.refresh(value, LockModeType.PESSIMISTIC_WRITE);
            double next;
            try {
                next = proposal.resolve(value.getCurrentValue(), definition.getMinValue(), definition.getMaxValue());
            } catch (IllegalArgumentException invalid) {
                throw new ProposalRejected(invalid);
            }
            changes.add(new ValidatedChange(value, next));
        }
        return changes;
    }

    private List<CustomStateSnapshot> mutate(List<ValidatedChange> changes) {
        // 전부 검증한 뒤에만 엔티티를 변경한다. 중간 조회의 자동 flush로 일부 제안이 저장되지 않게 한다.
        for (var change : changes) change.value().updateCurrentValue(change.next());
        em.flush();
        return changes.stream().map(change -> CustomStateSnapshot.from(change.value())).toList();
    }

    private record ValidatedChange(CustomStateValue value, double next) {}

    // 이 서비스가 확인한 입력/도메인 검증 실패만 표시한다. 영속성 예외를 잘못 삼키지 않는다.
    private static class ProposalRejected extends RuntimeException {
        private final RuntimeException reason;
        private ProposalRejected(RuntimeException reason) {
            super(reason);
            this.reason = reason;
        }
    }
}
