package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.relationship.RelationshipChangeService;
import com.example.projectmm.application.relationship.RelationshipFactService;
import com.example.projectmm.application.state.CustomStateProposalService;
import com.example.projectmm.application.simulation.*;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.dialogue.DialogueTurn;
import com.example.projectmm.domain.dialogue.DialogueStateChange;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationProgressService {
    private final ConversationRepository conversations;
    private final ConversationCatchUpRepository reports;
    private final CharacterStateRepository states;
    private final ChatMessageRepository messages;
    private final MemoryRepository memories;
    private final CatchUpSimulationService simulation;
    private final CatchUpSegmentService segments;
    private final CatchUpEventCandidateService candidates;
    private final EventCandidateValidator validator;
    private final CatchUpExecutionService catchUpExecution;
    private final PresenceClock clock;
    private final EntityManager em;
    private final CustomStateProposalService customStateProposals;
    private final RelationshipRepository relationshipRepository;
    private final RelationshipChangeService relationshipChangeService;
    private final RelationshipFactService relationshipFactService;
    private final CharacterRepository characterRepository;

    public record Report(Long id, LocalDateTime from, LocalDateTime to, long elapsedSeconds,
                         long appliedSeconds, boolean capped, String pauseReason, String actions) {}
    public record Status(boolean enabled, LocalDateTime simulatedAt, String pauseReason, Report lastReport,
                         int energy, int mood) {}
    public record ChatOperation(
            Conversation room,
            String token,
            NarrativeEntry entry
    ) {}
    public record CompletedChat(
            ChatMessage source,
            ChatStateUpdate stateUpdate,
            PlayerStateUpdate playerStateUpdate,
            CustomStateProposalService.ApplyStatus customStateStatus
    ) {}

    @Transactional(readOnly = true)
    public Status current(Long characterId, Long roomId) {
        var room = conversations.findById(roomId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!room.getCharacter().getId().equals(characterId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return status(room);
    }

    @Transactional
    public Status resume(Long characterId, Long roomId) {
        return resumeWithMode(characterId, roomId).status();
    }

    private ResumeResult resumeWithMode(
            Long characterId,
            Long roomId
    ) {
        var room = locked(characterId, roomId);
        var now = clock.now();

        requireIdle(room, now);

        var previousPresence = room.getLastPresenceAt();

        room.markPresent(now);

        if (!room.autoProgressEnabled()
                || room.getPlayerProfile() == null
                || room.getProgressPauseReason() != null
                || previousPresence == null) {

            return new ResumeResult(
                    status(room),
                    NarrativeEntry.DIRECT
            );
        }

        long seconds =
                Math.max(
                        0,
                        Duration.between(
                                previousPresence,
                                now
                        ).getSeconds()
                );

        if (seconds < 60) {
            return new ResumeResult(
                    status(room),
                    NarrativeEntry.DIRECT
            );
        }

        LocalDateTime from = room.getSimulatedAt();

        // 실제 날짜로 덮어쓰지 않고
        // 부재 시간만 이야기의 현재 시각에 더한다.
        var interval =
                simulation.calculate(
                        from,
                        from.plusSeconds(seconds)
                );

        var baseline =
                states.findByCharacterId(characterId)
                        .orElseThrow();

        // 공용 상태를 수정하지 않고
        // 계산용 복사본에 기존 규칙을 적용한다.
        var state = new CharacterState(
                room.getCharacter(),
                room.getEnergy() == null
                        ? baseline.getEnergy()
                        : room.getEnergy(),
                room.getMood() == null
                        ? baseline.getMood()
                        : room.getMood(),
                baseline.getMemoryAbility(),
                from
        );

        var actions = new ArrayList<String>();

        String pauseReason = null;

        for (var segment :
                segments.createSegments(
                        interval.from(),
                        interval.to()
                )) {

            var proposed =
                    candidates.generate(
                            state,
                            segment
                    );

            // 지원하지 않는 rule은 여전히 구간 전체를 보류한다. AI 판단은 기존 실행기에 맡긴다.
            if (proposed.stream().anyMatch(c ->
                    !c.requiresAi() && (!"REST".equals(c.eventType()) || !validator.validate(c).valid())
            )) {

                pauseReason =
                        "판단이 필요한 행동이 있어 자동 진행을 멈췄습니다. "
                                + "대화로 장면을 이어가 주세요.";

                break;
            }

            if (proposed.stream().anyMatch(c -> c.requiresAi())) {
                // 이 구간의 rule도 실행기가 한 번만 적용한다. 아래의 간이 적용 경로는 건너뛴다.
                var events = catchUpExecution.execute(state, segment, proposed, room);
                for (var event : events) {
                    String summary = room.getCharacter().getName() + " — " + event.getSummary();
                    messages.save(new ChatMessage(room, room.getCharacter(), ChatRole.CHARACTER,
                            "*" + summary + "*", ContentMode.NORMAL, now));
                    actions.add(summary);
                }
                continue;
            }

            for (var candidate : proposed) {

                state.apply(
                        candidate.stateDelta()
                );

                String summary =
                        room.getCharacter().getName()
                                + " — 피로가 누적되어 휴식을 취했다.";

                var source =
                        messages.save(
                                new ChatMessage(
                                        room,
                                        room.getCharacter(),
                                        ChatRole.CHARACTER,
                                        "*" + summary + "*",
                                        ContentMode.NORMAL,
                                        now
                                )
                        );

                memories.save(
                        Memory.fromAutomaticAction(
                                room,
                                source,
                                summary,
                                candidate.importance(),
                                candidate.occurredAt()
                        )
                );

                actions.add(summary);
            }

            state.advanceSimulatedAt(
                    segment.end()
            );
        }

        room.applyRoomState(
                state.getEnergy(),
                state.getMood(),
                state.getSimulatedAt()
        );

        room.pauseProgress(
                pauseReason
        );

        // 3일 상한 초과분과 보류 이후 시간은
        // 다음 접속에서 다시 가산하지 않는다.
        reports.save(
                new ConversationCatchUp(
                        room,
                        now,
                        from,
                        room.getSimulatedAt(),
                        seconds,
                        interval.capped(),
                        pauseReason,
                        String.join("\n", actions)
                )
        );

        return new ResumeResult(
                status(room),
                NarrativeEntry.AFTER_CATCH_UP
        );
    }

    @Transactional
    public Status configure(Long characterId, Long roomId, boolean enabled) {
        var room = locked(characterId, roomId);
        requireIdle(room, clock.now());
        if (room.getPlayerProfile() == null) throw conflict("유저 프로필을 먼저 선택해 주세요.");
        // 같은 설정의 재전송으로 미처리 구간이나 보류 상태를 지우지 않는다.
        if (enabled != room.autoProgressEnabled()) room.configureAutoProgress(enabled, clock.now());
        return status(room);
    }

    @Transactional
    public void heartbeat(Long characterId, Long roomId) {
        locked(characterId, roomId).markPresent(clock.now());
    }

    @Transactional
    public ChatOperation beginChat(
            Long characterId,
            Long roomId
    ) {
        // 네트워크 복구 후 입장 요청보다 전송이 먼저 와도
        // 부재 구간을 버리지 않는다.
        //
        // catch-up 실행 여부도 함께 받아
        // 상위 narrative 계층이 실제 실행 경로를 알 수 있게 한다.
        var resumed =
                resumeWithMode(
                        characterId,
                        roomId
                );

        var room =
                locked(
                        characterId,
                        roomId
                );

        requireIdle(
                room,
                clock.now()
        );

        if (room.getPlayerProfile() == null) {
            throw conflict(
                    "유저 프로필을 먼저 선택해 주세요."
            );
        }

        String token =
                UUID.randomUUID().toString();

        room.beginChat(
                token,
                clock.now()
        );

        return new ChatOperation(
                room,
                token,
                resumed.entry()
        );
    }

    private record ResumeResult(
            Status status,
            NarrativeEntry entry
    ) {}

    @Transactional
    public void applyChatTime(Long characterId, Long roomId, String token, LocalDateTime at) {
        ownedOperation(characterId, roomId, token).updateSimulatedAt(at);
    }

    @Transactional
    public ChatMessage completeChat(Long characterId, Long roomId, String token,
            String userText, String reply, ContentMode mode) {
        return completeChat(characterId, roomId, token, userText,
                new DialogueTurn(reply, DialogueStateChange.none()), mode).source();
    }

    @Transactional
    public CompletedChat completeChat(Long characterId, Long roomId, String token,
            String userText, DialogueTurn turn, ContentMode mode) {
        var room = ownedOperation(characterId, roomId, token);
        if (turn == null || turn.reply() == null || turn.reply().isBlank())
            throw new IllegalArgumentException("대화 본문이 비어 있습니다.");
        // 별도 프록시의 MANDATORY 경로가 이 트랜잭션에 참여한다. 중복 room refresh 전에 NPC를 변경하지 않는다.
        var customStatus = turn.customStateChanges().isEmpty() ? CustomStateProposalService.ApplyStatus.NONE
                : customStateProposals.applyForChat(roomId, turn.customStateChanges());
        var baseline = states.findByCharacterId(characterId).orElseThrow();
        var update = ChatStateUpdate.resolve(
                room.getEnergy() == null ? baseline.getEnergy() : room.getEnergy(),
                room.getMood() == null ? baseline.getMood() : room.getMood(),
                turn.reply(),
                turn.stateChange()
        );

        var playerUpdate = PlayerStateUpdate.resolve(
                room.getPlayerEnergy(),
                room.getPlayerMood(),
                userText,
                turn.playerStateChange()
        );

        applyRelationshipChange(
                characterId,
                turn
        );

// NPC와 플레이어의 확정 상태를 같은 채팅 트랜잭션에서 반영한다.
        room.applyRoomState(
                update.energy(),
                update.mood(),
                room.getSimulatedAt()
        );

        room.applyPlayerState(
                playerUpdate.energy(),
                playerUpdate.mood()
        );
        var now = clock.now();

        ChatMessage source = null;

// 사용자가 실제 내용을 입력한 경우에만 USER 메시지를 저장한다.
// 빈 입력은 기존 장면의 계속 생성을 요청하는 신호이므로
// 가짜 USER 메시지를 대화 이력에 남기지 않는다.
        if (userText != null && !userText.isBlank()) {
            source =
                    messages.save(
                            new ChatMessage(
                                    room,
                                    room.getCharacter(),
                                    ChatRole.USER,
                                    userText,
                                    mode,
                                    now
                            )
                    );
        }

        ChatMessage characterMessage =
                messages.save(
                        new ChatMessage(
                                room,
                                room.getCharacter(),
                                ChatRole.CHARACTER,
                                turn.reply(),
                                mode,
                                source == null
                                        ? now
                                        : now.plusNanos(1000)
                        )
                );

        applyRelationshipFactChange(
                room.getCharacter(),
                turn,
                characterMessage,
                now
        );

        room.endChat(token, now);

        return new CompletedChat(
                source,
                update,
                playerUpdate,
                customStatus
        );
    }

    private void applyRelationshipChange(
            Long characterId,
            DialogueTurn turn
    ) {
        var change =
                turn.relationshipChange();

        if (change == null
                || !change.hasChange()) {
            return;
        }

        Long targetCharacterId =
                change.targetCharacterId();

        if (targetCharacterId == null
                || change.delta() == null) {
            return;
        }

        var delta =
                change.delta();

        if (delta.affinityChange() < -10
                || delta.affinityChange() > 10
                || delta.trustChange() < -10
                || delta.trustChange() > 10
                || delta.tensionChange() < -10
                || delta.tensionChange() > 10) {
            return;
        }

        boolean relationshipExists =
                relationshipRepository
                        .findByFromCharacterIdAndToCharacterId(
                                characterId,
                                targetCharacterId
                        )
                        .isPresent();

        if (!relationshipExists) {
            return;
        }

        relationshipChangeService.apply(
                characterId,
                targetCharacterId,
                delta
        );
    }

    private void applyRelationshipFactChange(
            com.example.projectmm.domain.character.Character fromCharacter,
            DialogueTurn turn,
            ChatMessage sourceChatMessage,
            LocalDateTime establishedAt
    ) {
        var change =
                turn.relationshipFactChange();

        if (change == null
                || !change.hasChange()) {
            return;
        }

        Long targetCharacterId =
                change.targetCharacterId();

        if (targetCharacterId == null
                || change.relation() == null
                || change.relation().isBlank()
                || change.value() == null
                || change.value().isBlank()) {
            return;
        }

        boolean relationshipExists =
                relationshipRepository
                        .findByFromCharacterIdAndToCharacterId(
                                fromCharacter.getId(),
                                targetCharacterId
                        )
                        .isPresent();

        if (!relationshipExists) {
            return;
        }

        var targetCharacter =
                characterRepository
                        .findById(targetCharacterId)
                        .orElse(null);

        if (targetCharacter == null) {
            return;
        }

        relationshipFactService.establishFromChatMessage(
                fromCharacter,
                targetCharacter,
                change.relation(),
                change.value(),
                sourceChatMessage,
                establishedAt
        );
    }

    @Transactional
    public void endChat(Long characterId, Long roomId, String token) {
        locked(characterId, roomId).endChat(token, clock.now());
    }

    private Conversation ownedOperation(Long characterId, Long roomId, String token) {
        var room = locked(characterId, roomId);
        if (!token.equals(room.getChatOperationId()) || !room.chatInProgress(clock.now()))
            throw conflict("대화 처리 시간이 만료되었습니다. 다시 시도해 주세요.");
        return room;
    }

    private Conversation locked(Long characterId, Long roomId) {
        var room = conversations.lockForMemory(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // OSIV에서 먼저 조회한 객체도 잠금 획득 이후의 DB 값으로 갱신한다.
        em.refresh(room);
        if (!room.getCharacter().getId().equals(characterId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return room;
    }

    private void requireIdle(Conversation room, LocalDateTime now) {
        if (room.chatInProgress(now)) throw conflict("답변을 생성 중입니다. 잠시 후 다시 시도해 주세요.");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private Status status(Conversation room) {
        var report = reports.findTopByConversationIdOrderByIdDesc(room.getId()).map(r -> new Report(
                r.getId(), r.getFromTime(), r.getToTime(), r.getElapsedSeconds(), r.getAppliedSeconds(),
                r.isCapped(), r.getPauseReason(), r.getActions())).orElse(null);
        var baseline = states.findByCharacterId(room.getCharacter().getId()).orElseThrow();
        return new Status(room.autoProgressEnabled(), room.getSimulatedAt(), room.getProgressPauseReason(), report,
                room.getEnergy() == null ? baseline.getEnergy() : room.getEnergy(),
                room.getMood() == null ? baseline.getMood() : room.getMood());
    }
}
