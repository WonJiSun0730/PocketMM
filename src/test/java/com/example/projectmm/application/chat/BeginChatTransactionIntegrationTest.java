package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.simulation.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.simulation.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

// 실제 commit/별도 connection이 필요한 진입·claim·동시성 검증만 담당한다.
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class BeginChatTransactionIntegrationTest extends ConversationProgressTestSupport {

    private record CommittedFixture(Long roomId, Long profileId, Long characterId) {}
    private final java.util.List<CommittedFixture> committedFixtures = new java.util.ArrayList<>();

    @Override
    Conversation fixture(boolean independent, int energy, int mood) {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var room = tx.execute(status -> super.fixture(independent, energy, mood));
        committedFixtures.add(new CommittedFixture(room.getId(), room.getPlayerProfile().getId(), room.getCharacter().getId()));
        return room;
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupCommittedFixtures() {
        // 새 integration case도 assertion 실패 여부와 무관하게 자기 fixture만 정리한다.
        for (var fixture : java.util.List.copyOf(committedFixtures)) {
            cleanupCommittedFixture(fixture.roomId(), fixture.profileId(), fixture.characterId());
            assertThat(rooms.findById(fixture.roomId())).isEmpty();
        }
        committedFixtures.clear();
    }
    @Test void sendingFirstAfterNetworkRecoveryStillAppliesMissingInterval() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();
        service.configure(id, room.getId(), true);
        when(clock.now()).thenReturn(real.plusMinutes(4));
        var operation = service.beginChat(id, room.getId());
        assertThat(operation.room().getSimulatedAt()).isEqualTo(story.plusMinutes(4));
        assertThat(service.current(id, room.getId()).lastReport().appliedSeconds()).isEqualTo(240);
        service.endChat(id, room.getId(), operation.token());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousReconnectsCommitOneReportAndOneRest() throws Exception {
        // 별도 커넥션의 실제 트랜잭션 두 개로 잠금과 체크포인트의 중복 방지를 검증한다.
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var room = tx.execute(status -> fixture(true, 20, 70));
        Long id = room.getCharacter().getId();
        service.configure(id, room.getId(), true);
        when(clock.now()).thenReturn(real.plusMinutes(2));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<ConversationProgressService.Status> reconnect = () -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 초과");
                return service.resume(id, room.getId());
            };
            var first = executor.submit(reconnect);
            var second = executor.submit(reconnect);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var firstReport = first.get(10, java.util.concurrent.TimeUnit.SECONDS).lastReport();
            var secondReport = second.get(10, java.util.concurrent.TimeUnit.SECONDS).lastReport();
            assertThat(firstReport.id()).isEqualTo(secondReport.id());
            assertThat(rooms.findById(room.getId()).orElseThrow().getSimulatedAt()).isEqualTo(story.plusMinutes(2));
            assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).hasSize(1);
        } finally {
            // 이 테스트만 커밋하므로 자신이 만든 데이터만 격리 DB에서 정리한다.
            tx.executeWithoutResult(status -> {
                em.createQuery("delete from ConversationCatchUp r where r.conversation.id = :id").setParameter("id", room.getId()).executeUpdate();
                em.createQuery("delete from Memory m where m.conversation.id = :id").setParameter("id", room.getId()).executeUpdate();
                em.createQuery("delete from ChatMessage m where m.conversation.id = :id").setParameter("id", room.getId()).executeUpdate();
                em.remove(em.find(Conversation.class, room.getId()));
                em.flush();
                em.remove(em.find(PlayerProfile.class, room.getPlayerProfile().getId()));
                em.createQuery("delete from CharacterState s where s.character.id = :id").setParameter("id", id).executeUpdate();
                em.remove(em.find(Character.class, id));
            });
        }
    }

    @Test
    void beginChatReportsLiveDialogueWhenCatchUpIsNotNeeded() {
        var room = fixture(true, 70, 70);
        Long characterId = room.getCharacter().getId();

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        assertThat(operation.entry())
                .isEqualTo(NarrativeEntry.DIRECT);

        service.endChat(
                characterId,
                room.getId(),
                operation.token()
        );
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void beginChatReportsCatchUpWhenAbsenceWasActuallyProcessed() {

        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        var room =
                tx.execute(status ->
                        fixture(true, 70, 70)
                );

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        try {
            service.configure(
                    characterId,
                    room.getId(),
                    true
            );

            when(clock.now())
                    .thenReturn(real.plusMinutes(4));

            org.mockito.Mockito.doAnswer(invocation -> {
                assertThat(
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .isActualTransactionActive()
                ).isFalse();

                CharacterState state =
                        invocation.getArgument(0);

                CatchUpSegment segment =
                        invocation.getArgument(1);

                state.advanceSimulatedAt(
                        segment.end()
                );

                var duration =
                        java.time.Duration.between(
                                segment.start(),
                                segment.end()
                        );

                return new CatchUpExecutionResult(
                        new CatchUpResult(
                                segment.start(),
                                segment.end(),
                                duration,
                                duration,
                                false
                        ),
                        java.util.List.of(segment),
                        java.util.List.of(),
                        java.util.List.of()
                );
            }).when(execution).resolve(
                    org.mockito.ArgumentMatchers.any(CharacterState.class),
                    org.mockito.ArgumentMatchers.any(CatchUpSegment.class),
                    org.mockito.ArgumentMatchers.anyList(),
                    org.mockito.ArgumentMatchers.any(Conversation.class)
            );

            var operation =
                    service.beginChat(
                            characterId,
                            room.getId()
                    );

            assertThat(operation.entry())
                    .isEqualTo(NarrativeEntry.AFTER_CATCH_UP);

            assertThat(operation.room().getSimulatedAt())
                    .isEqualTo(story.plusMinutes(4));

            assertThat(
                    service.current(
                            characterId,
                            room.getId()
                    ).lastReport().appliedSeconds()
            ).isEqualTo(240);

            service.endChat(
                    characterId,
                    room.getId(),
                    operation.token()
            );

        } finally {
            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void secondBeginChatFailsFastWhileFirstIsWaitingForAiResolve() throws Exception {

        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        LocalDateTime previousPresence =
                real.minusMinutes(4);

        var room =
                tx.execute(status -> {
                    var created =
                            fixture(true, 70, 70);

                    created.configureAutoProgress(
                            true,
                            previousPresence
                    );

                    created.markPresent(
                            previousPresence
                    );

                    return created;
                });

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        var resolveEntered =
                new java.util.concurrent.CountDownLatch(1);

        var allowResolve =
                new java.util.concurrent.CountDownLatch(1);

        try {
            org.mockito.Mockito.doReturn(
                    java.util.List.of(
                            new EventCandidate(
                                    story,
                                    "AI_TEST",
                                    "concurrent beginChat test",
                                    5,
                                    true,
                                    StateDelta.none()
                            )
                    )
            ).when(candidateService).generate(
                    org.mockito.ArgumentMatchers.any(CharacterState.class),
                    org.mockito.ArgumentMatchers.any(CatchUpSegment.class)
            );

            org.mockito.Mockito.doAnswer(invocation -> {
                assertThat(
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .isActualTransactionActive()
                ).isFalse();

                resolveEntered.countDown();

                if (!allowResolve.await(
                        5,
                        java.util.concurrent.TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "AI resolve release timeout"
                    );
                }

                CharacterState state =
                        invocation.getArgument(0);

                CatchUpSegment segment =
                        invocation.getArgument(1);

                state.advanceSimulatedAt(
                        segment.end()
                );

                var duration =
                        java.time.Duration.between(
                                segment.start(),
                                segment.end()
                        );

                return new CatchUpExecutionResult(
                        new CatchUpResult(
                                segment.start(),
                                segment.end(),
                                duration,
                                duration,
                                false
                        ),
                        java.util.List.of(segment),
                        java.util.List.of(),
                        java.util.List.of()
                );
            }).when(execution).resolve(
                    org.mockito.ArgumentMatchers.any(CharacterState.class),
                    org.mockito.ArgumentMatchers.any(CatchUpSegment.class),
                    org.mockito.ArgumentMatchers.anyList(),
                    org.mockito.ArgumentMatchers.any(Conversation.class)
            );

            try (var executor =
                         java.util.concurrent.Executors.newFixedThreadPool(2)) {

                var first =
                        executor.submit(() ->
                                service.beginChat(
                                        characterId,
                                        room.getId()
                                )
                        );

                assertThat(
                        resolveEntered.await(
                                5,
                                java.util.concurrent.TimeUnit.SECONDS
                        )
                ).isTrue();

                /*
                 * 첫 요청은 지금 AI resolve에서 대기 중이다.
                 *
                 * prepare()의 claim이 이미 커밋됐고
                 * resolve 구간에는 DB transaction이 없어야 하므로,
                 * 두 번째 요청은 첫 요청의 DB lock 해제를 기다리지 않고
                 * committed claim을 읽어 빠르게 CONFLICT로 끝나야 한다.
                 */
                var second =
                        executor.submit(() ->
                                service.beginChat(
                                        characterId,
                                        room.getId()
                                )
                        );

                var secondFailure =
                        catchThrowable(() ->
                                second.get(
                                        2,
                                        java.util.concurrent.TimeUnit.SECONDS
                                )
                        );

                assertThat(secondFailure)
                        .isInstanceOf(
                                java.util.concurrent.ExecutionException.class
                        );

                assertThat(secondFailure.getCause())
                        .isInstanceOf(
                                org.springframework.web.server.ResponseStatusException.class
                        );

                var conflict =
                        (org.springframework.web.server.ResponseStatusException)
                                secondFailure.getCause();

                assertThat(conflict.getStatusCode().value())
                        .isEqualTo(409);

                allowResolve.countDown();

                var operation =
                        first.get(
                                5,
                                java.util.concurrent.TimeUnit.SECONDS
                        );

                assertThat(operation.entry())
                        .isEqualTo(NarrativeEntry.AFTER_CATCH_UP);

                service.endChat(
                        characterId,
                        room.getId(),
                        operation.token()
                );
            }

        } finally {
            /*
             * assertion 실패가 resolve release 전에 발생해도
             * 첫 worker가 latch에서 남지 않게 반드시 열어 둔다.
             */
            allowResolve.countDown();

            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void beginChatReleasesCatchUpClaimWithoutConsumingPresenceWhenResolveFails() {

        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        LocalDateTime previousPresence =
                real.minusMinutes(4);

        var room =
                tx.execute(status -> {
                    var created =
                            fixture(true, 70, 70);

                    created.configureAutoProgress(
                            true,
                            previousPresence
                    );

                    created.markPresent(
                            previousPresence
                    );

                    return created;
                });

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        try {

        org.mockito.Mockito.doReturn(
                java.util.List.of(
                        new EventCandidate(
                                story,
                                "AI_TEST",
                                "AI resolve failure test",
                                5,
                                true,
                                StateDelta.none()
                        )
                )
        ).when(candidateService).generate(
                org.mockito.ArgumentMatchers.any(CharacterState.class),
                org.mockito.ArgumentMatchers.any(CatchUpSegment.class)
        );

        org.mockito.Mockito.doThrow(
                new IllegalStateException("AI resolve failure")
        ).when(execution).resolve(
                org.mockito.ArgumentMatchers.any(CharacterState.class),
                org.mockito.ArgumentMatchers.any(CatchUpSegment.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(Conversation.class)
        );

        assertThatThrownBy(() ->
                service.beginChat(
                        characterId,
                        room.getId()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI resolve failure");

        var saved =
                rooms.findById(
                        room.getId()
                ).orElseThrow();

        assertThat(saved.getCatchUpOperationId())
                .isNull();

        assertThat(saved.catchUpInProgress(real))
                .isFalse();

        assertThat(saved.getLastPresenceAt())
                .isEqualTo(previousPresence);

        assertThat(saved.getSimulatedAt())
                .isEqualTo(story);

        } finally {
            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    @Test
    void catchUpBeforeChatPersistsNarrativeContextForFollowingLiveDialogue() {
        var room = fixture(true, 20, 70);
        Long characterId = room.getCharacter().getId();

        service.configure(
                characterId,
                room.getId(),
                true
        );

        when(clock.now())
                .thenReturn(real.plusHours(1));

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        assertThat(operation.entry())
                .isEqualTo(NarrativeEntry.AFTER_CATCH_UP);

        var savedMessages =
                messages.findTop20ByConversationIdOrderByCreatedAtDesc(
                        room.getId()
                );

        assertThat(savedMessages)
                .hasSize(1);

        assertThat(savedMessages.getFirst().getRole())
                .isEqualTo(ChatRole.CHARACTER);

        assertThat(savedMessages.getFirst().getContent())
                .contains("휴식");

        assertThat(
                memories.findForConversation(
                        characterId,
                        room.getId()
                )
        ).hasSize(1);

        service.endChat(
                characterId,
                room.getId(),
                operation.token()
        );
    }

    @Test
    void beginChat은_부재CatchUp이_없으면_DIRECT로_진입한다() {

        var room =
                fixture(
                        true,
                        70,
                        70
                );

        Long characterId =
                room.getCharacter().getId();

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        assertThat(operation.entry())
                .isEqualTo(
                        NarrativeEntry.DIRECT
                );

        assertThat(operation.room().getId())
                .isEqualTo(room.getId());

        assertThat(operation.token())
                .isNotBlank();
    }

    @Test
    void beginChat은_실제_부재CatchUp을_처리한뒤_AFTER_CATCH_UP으로_진입한다() {

        var room =
                fixture(
                        true,
                        70,
                        70
                );

        Long characterId =
                room.getCharacter().getId();

        // 첫 접속으로 lastPresenceAt을 만든다.
        service.resume(
                characterId,
                room.getId()
        );

        // 이후 자동 진행을 켠다.
        service.configure(
                characterId,
                room.getId(),
                true
        );

        // 실제 시간 기준 2분 부재.
        when(clock.now())
                .thenReturn(
                        real.plusMinutes(2)
                );

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        assertThat(operation.entry())
                .isEqualTo(
                        NarrativeEntry.AFTER_CATCH_UP
                );

        assertThat(operation.room().getId())
                .isEqualTo(room.getId());

        assertThat(operation.token())
                .isNotBlank();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void catchUpClaimCommitsImmediatelyAndBlocksSecondClaim() {
        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        var room =
                tx.execute(status ->
                        fixture(true, 70, 70)
                );

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        try {
            String firstToken =
                    catchUpClaims.claim(
                            characterId,
                            room.getId(),
                            real
                    );

            assertThat(firstToken)
                    .isNotBlank();

            var committed =
                    rooms.findById(room.getId())
                            .orElseThrow();

            assertThat(committed.getCatchUpOperationId())
                    .isEqualTo(firstToken);

            assertThat(committed.catchUpInProgress(real))
                    .isTrue();

            assertThatThrownBy(() ->
                    catchUpClaims.claim(
                            characterId,
                            room.getId(),
                            real
                    )
            ).isInstanceOf(
                    org.springframework.web.server.ResponseStatusException.class
            );

            catchUpClaims.release(
                    characterId,
                    room.getId(),
                    firstToken
            );

            var released =
                    rooms.findById(room.getId())
                            .orElseThrow();

            assertThat(released.catchUpInProgress(real))
                    .isFalse();

        } finally {
            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void finalizeRejectsChangedCatchUpClaimBeforePersistence() {

        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        LocalDateTime previousPresence =
                real.minusMinutes(4);

        var room =
                tx.execute(status -> {
                    var created =
                            fixture(true, 70, 70);

                    created.configureAutoProgress(
                            true,
                            previousPresence
                    );

                    created.markPresent(
                            previousPresence
                    );

                    return created;
                });

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        try {
            var preparation =
                    beginChatPreparation.prepare(
                            characterId,
                            room.getId()
                    );

            assertThat(preparation.catchUpRequired())
                    .isTrue();

            catchUpClaims.release(
                    characterId,
                    room.getId(),
                    preparation.claimToken()
            );

            String replacementClaim =
                    catchUpClaims.claim(
                            characterId,
                            room.getId(),
                            real
                    );

            assertThat(replacementClaim)
                    .isNotEqualTo(preparation.claimToken());

            var state =
                    new CharacterState(
                            room.getCharacter(),
                            preparation.roomEnergy(),
                            preparation.roomMood(),
                            100,
                            preparation.simulatedAt()
                    );

            var resolution =
                    new BeginChatCatchUpResolver.Resolution(
                            state,
                            java.util.List.of(),
                            java.util.List.of(),
                            null,
                            false
                    );

            assertThatThrownBy(() ->
                    beginChatFinalization.finalizeBeginChat(
                            preparation,
                            resolution
                    )
            )
                    .isInstanceOf(
                            org.springframework.web.server.ResponseStatusException.class
                    )
                    .satisfies(error -> {
                        var response =
                                (org.springframework.web.server.ResponseStatusException)
                                        error;

                        assertThat(response.getStatusCode().value())
                                .isEqualTo(409);
                    });

            var saved =
                    rooms.findById(
                            room.getId()
                    ).orElseThrow();

            assertThat(saved.getCatchUpOperationId())
                    .isEqualTo(replacementClaim);

            assertThat(saved.getLastPresenceAt())
                    .isEqualTo(previousPresence);

            assertThat(saved.getSimulatedAt())
                    .isEqualTo(story);

            assertThat(
                    reports.findTopByConversationIdOrderByIdDesc(
                            room.getId()
                    )
            ).isEmpty();

            assertThat(
                    messages.findTop20ByConversationIdOrderByCreatedAtDesc(
                            room.getId()
                    )
            ).isEmpty();

            catchUpClaims.release(
                    characterId,
                    room.getId(),
                    replacementClaim
            );

        } finally {
            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void activeCatchUpClaimBlocksNormalRoomOperations() {

        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        var room =
                tx.execute(status ->
                        fixture(true, 70, 70)
                );

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        try {
            String claim =
                    catchUpClaims.claim(
                            characterId,
                            room.getId(),
                            real
                    );

            assertThatThrownBy(() ->
                    service.resume(
                            characterId,
                            room.getId()
                    )
            ).isInstanceOf(
                    org.springframework.web.server.ResponseStatusException.class
            );

            assertThatThrownBy(() ->
                    service.configure(
                            characterId,
                            room.getId(),
                            true
                    )
            ).isInstanceOf(
                    org.springframework.web.server.ResponseStatusException.class
            );

            assertThatThrownBy(() ->
                    service.beginChat(
                            characterId,
                            room.getId()
                    )
            ).isInstanceOf(
                    org.springframework.web.server.ResponseStatusException.class
            );

            assertThatThrownBy(() ->
                    service.heartbeat(
                            characterId,
                            room.getId()
                    )
            ).isInstanceOf(
                    org.springframework.web.server.ResponseStatusException.class
            );

            catchUpClaims.release(
                    characterId,
                    room.getId(),
                    claim
            );

        } finally {
            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void beginChatPreparationClaimsFixedAbsenceWithoutConsumingPresence() {

        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        LocalDateTime previousPresence =
                real.minusHours(2);

        var room =
                tx.execute(status -> {
                    var created =
                            fixture(true, 70, 70);

                    created.configureAutoProgress(
                            true,
                            previousPresence
                    );

                    created.markPresent(
                            previousPresence
                    );

                    return created;
                });

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        try {

        var preparation =
                beginChatPreparation.prepare(
                        characterId,
                        room.getId()
                );

        assertThat(preparation.claimToken())
                .isNotBlank();

        assertThat(preparation.previousPresence())
                .isEqualTo(previousPresence);

        assertThat(preparation.requestedAt())
                .isEqualTo(real);

        assertThat(preparation.absenceSeconds())
                .isEqualTo(7200);

        assertThat(preparation.catchUpRequired())
                .isTrue();

        assertThat(preparation.simulatedAt())
                .isEqualTo(story);

        assertThat(preparation.roomEnergy())
                .isEqualTo(70);

        assertThat(preparation.roomMood())
                .isEqualTo(70);

        var committed =
                rooms.findById(
                        room.getId()
                ).orElseThrow();

        assertThat(committed.getLastPresenceAt())
                .isEqualTo(previousPresence);

        assertThat(committed.getCatchUpOperationId())
                .isEqualTo(preparation.claimToken());

        assertThat(committed.catchUpInProgress(real))
                .isTrue();

        catchUpClaims.release(
                characterId,
                room.getId(),
                preparation.claimToken()
        );
        } finally {
            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void beginChatPreparationSkipsCatchUpForShortAbsence() {

        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        var room =
                tx.execute(status -> {
                    var created =
                            fixture(true, 70, 70);

                    created.markPresent(
                            real.minusSeconds(30)
                    );

                    return created;
                });

        Long characterId =
                room.getCharacter().getId();

        Long profileId =
                room.getPlayerProfile().getId();

        try {

        var preparation =
                beginChatPreparation.prepare(
                        characterId,
                        room.getId()
                );

        assertThat(preparation.absenceSeconds())
                .isEqualTo(30);

        assertThat(preparation.catchUpRequired())
                .isFalse();

        catchUpClaims.release(
                characterId,
                room.getId(),
                preparation.claimToken()
        );
        } finally {
            cleanupCommittedFixture(
                    room.getId(),
                    profileId,
                    characterId
            );
        }
    }

    private void cleanupCommittedFixture(
            Long roomId,
            Long profileId,
            Long characterId
    ) {
        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                );

        tx.executeWithoutResult(status -> {
            em.createQuery(
                            "delete from ConversationCatchUp r where r.conversation.id = :id"
                    )
                    .setParameter("id", roomId)
                    .executeUpdate();

            em.createQuery(
                            "delete from Memory m where m.conversation.id = :id"
                    )
                    .setParameter("id", roomId)
                    .executeUpdate();

            em.createQuery(
                            "delete from ChatMessage m where m.conversation.id = :id"
                    )
                    .setParameter("id", roomId)
                    .executeUpdate();

            var conversation =
                    em.find(
                            Conversation.class,
                            roomId
                    );

            if (conversation != null) {
                em.remove(conversation);
                em.flush();
            }

            var profile =
                    em.find(
                            PlayerProfile.class,
                            profileId
                    );

            if (profile != null) {
                em.remove(profile);
            }

            em.createQuery(
                            "delete from CharacterState s where s.character.id = :id"
                    )
                    .setParameter("id", characterId)
                    .executeUpdate();

            var character =
                    em.find(
                            Character.class,
                            characterId
                    );

            if (character != null) {
                em.remove(character);
            }
        });
    }

}
