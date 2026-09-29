package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.simulation.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.player.PlayerProfile;
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

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({
        ConversationProgressService.class,
        CatchUpSimulationService.class,
        CatchUpSegmentService.class,
        CatchUpEventCandidateService.class,
        EventCandidateValidator.class,
        com.example.projectmm.application.state.CustomStateProposalService.class,
        com.example.projectmm.application.relationship.RelationshipChangeService.class,
        com.example.projectmm.application.relationship.RelationshipChangeService.class,
        com.example.projectmm.application.relationship.RelationshipFactService.class
})
class ConversationProgressTest {
    @Autowired EntityManager em;
    @Autowired ConversationProgressService service;
    @Autowired ConversationRepository rooms;
    @Autowired ConversationCatchUpRepository reports;
    @Autowired ChatMessageRepository messages;
    @Autowired MemoryRepository memories;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean PresenceClock clock;
    private final LocalDateTime real = LocalDateTime.of(2026, 9, 23, 12, 0);
    private final LocalDateTime story = LocalDateTime.of(2030, 1, 1, 23, 0);

    @BeforeEach void time() { when(clock.now()).thenReturn(real); }

    @Test
    void playerStateAppliesFromExactUserEvidence() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();

        var operation = service.beginChat(id, room.getId());

        String userText = "*두 시간 동안 달리고 나니 완전히 지쳤다.*";

        var turn = new com.example.projectmm.domain.dialogue.DialogueTurn(
                "*그 모습을 바라보았다.*",
                com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                new com.example.projectmm.domain.dialogue.DialoguePlayerStateChange(
                        -6,
                        0,
                        "두 시간 동안 달리고 나니 완전히 지쳤다",
                        ""
                ),
                java.util.List.of()
        );

        var completed = service.completeChat(
                id,
                room.getId(),
                operation.token(),
                userText,
                turn,
                ContentMode.NORMAL
        );

        assertThat(completed.playerStateUpdate().status())
                .isEqualTo("APPLIED");
        assertThat(completed.playerStateUpdate().previousEnergy())
                .isEqualTo(70);
        assertThat(completed.playerStateUpdate().energy())
                .isEqualTo(64);

        em.flush();
        em.clear();

        var saved = rooms.findById(room.getId()).orElseThrow();

        assertThat(saved.getPlayerEnergy()).isEqualTo(64);
        assertThat(saved.getPlayerMood()).isEqualTo(70);
    }

    @Test
    void playerStateRejectsEvidenceInventedOnlyByAiReply() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();

        var operation = service.beginChat(id, room.getId());

        var turn = new com.example.projectmm.domain.dialogue.DialogueTurn(
                "*하린은 갑자기 몹시 지쳐 보였다.*",
                com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                new com.example.projectmm.domain.dialogue.DialoguePlayerStateChange(
                        -8,
                        0,
                        "갑자기 몹시 지쳐 보였다",
                        ""
                ),
                java.util.List.of()
        );

        var completed = service.completeChat(
                id,
                room.getId(),
                operation.token(),
                "안녕하세요.",
                turn,
                ContentMode.NORMAL
        );

        assertThat(completed.playerStateUpdate().status())
                .isEqualTo("REJECTED");
        assertThat(completed.playerStateUpdate().energy())
                .isEqualTo(70);

        em.flush();
        em.clear();

        assertThat(
                rooms.findById(room.getId())
                        .orElseThrow()
                        .getPlayerEnergy()
        ).isEqualTo(70);

        assertThat(
                messages.findTop20ByConversationIdOrderByCreatedAtDesc(
                        room.getId()
                )
        ).hasSize(2);
    }

    @Test
    void playerStateCannotApplyTwiceWithCompletedOperationToken() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();

        var operation = service.beginChat(id, room.getId());

        String userText = "*한참 뛰어서 숨이 찼다.*";

        var turn = new com.example.projectmm.domain.dialogue.DialogueTurn(
                "*그 모습을 지켜보았다.*",
                com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                new com.example.projectmm.domain.dialogue.DialoguePlayerStateChange(
                        -5,
                        0,
                        "한참 뛰어서 숨이 찼다",
                        ""
                ),
                java.util.List.of()
        );

        service.completeChat(
                id,
                room.getId(),
                operation.token(),
                userText,
                turn,
                ContentMode.NORMAL
        );

        assertThatThrownBy(() ->
                service.completeChat(
                        id,
                        room.getId(),
                        operation.token(),
                        userText,
                        turn,
                        ContentMode.NORMAL
                )
        ).isInstanceOf(
                org.springframework.web.server.ResponseStatusException.class
        );

        em.flush();
        em.clear();

        assertThat(
                rooms.findById(room.getId())
                        .orElseThrow()
                        .getPlayerEnergy()
        ).isEqualTo(65);
    }

    @Test
    void playerStateProposalUsesLatestUserCorrectedValue() {
        var room = fixture(true, 70, 70);
        Long characterId = room.getCharacter().getId();

        room.applyPlayerState(80, 70);
        em.flush();

        var operation = service.beginChat(
                characterId,
                room.getId()
        );

        var turn = new com.example.projectmm.domain.dialogue.DialogueTurn(
                "많이 달렸군.",
                com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                new com.example.projectmm.domain.dialogue.DialoguePlayerStateChange(
                        -5,
                        0,
                        "두 시간 동안 달렸다",
                        ""
                ),
                java.util.List.of()
        );

        var completed = service.completeChat(
                characterId,
                room.getId(),
                operation.token(),
                "두 시간 동안 달렸다",
                turn,
                ContentMode.NORMAL
        );

        assertThat(completed.playerStateUpdate().status())
                .isEqualTo("APPLIED");
        assertThat(completed.playerStateUpdate().previousEnergy())
                .isEqualTo(80);
        assertThat(completed.playerStateUpdate().energy())
                .isEqualTo(75);

        em.flush();
        em.clear();

        var saved = rooms.findById(room.getId()).orElseThrow();

        assertThat(saved.getPlayerEnergy()).isEqualTo(75);
        assertThat(saved.getPlayerMood()).isEqualTo(70);
    }

    @Test void optInDoesNotBackfillAndResumeAddsAbsenceToStoryClockOnlyOnce() {
        var room = fixture(true, 70, 70);
        Long characterId = room.getCharacter().getId();
        service.resume(characterId, room.getId());
        when(clock.now()).thenReturn(real.plusDays(5));
        assertThat(service.resume(characterId, room.getId()).simulatedAt()).isEqualTo(story);
        service.configure(characterId, room.getId(), true);
        when(clock.now()).thenReturn(real.plusDays(5).plusMinutes(2));
        var applied = service.resume(characterId, room.getId());
        assertThat(applied.simulatedAt()).isEqualTo(story.plusMinutes(2));
        assertThat(applied.lastReport().appliedSeconds()).isEqualTo(120);
        assertThat(service.resume(characterId, room.getId()).lastReport().id()).isEqualTo(applied.lastReport().id());
        assertThat(reports.count()).isEqualTo(1);
        em.flush(); em.clear();
        assertThat(rooms.findById(room.getId()).orElseThrow().getSimulatedAt()).isEqualTo(story.plusMinutes(2));
    }

    @Test void visiblePresenceAndShortRefreshDoNotProgressAndClockRollbackDoesNotReplay() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();
        service.configure(id, room.getId(), true);
        for (int i = 1; i <= 10; i++) {
            when(clock.now()).thenReturn(real.plusSeconds(i * 30));
            service.heartbeat(id, room.getId());
        }
        when(clock.now()).thenReturn(real.plusMinutes(5).plusSeconds(40));
        assertThat(service.resume(id, room.getId()).lastReport()).isNull();
        when(clock.now()).thenReturn(real.minusDays(1));
        assertThat(service.resume(id, room.getId()).simulatedAt()).isEqualTo(story);
        when(clock.now()).thenReturn(real.plusMinutes(6).plusSeconds(40));
        assertThat(service.resume(id, room.getId()).lastReport().appliedSeconds()).isEqualTo(60);
    }

    @Test void threeDayCapDiscardsBacklogAndDisabledPeriodIsNotReplayed() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();
        service.configure(id, room.getId(), true);
        when(clock.now()).thenReturn(real.plusDays(10));
        var first = service.resume(id, room.getId());
        assertThat(first.lastReport().capped()).isTrue();
        assertThat(first.simulatedAt()).isEqualTo(story.plusDays(3));
        assertThat(service.resume(id, room.getId()).lastReport().id()).isEqualTo(first.lastReport().id());
        service.configure(id, room.getId(), false);
        when(clock.now()).thenReturn(real.plusDays(20));
        service.configure(id, room.getId(), true);
        assertThat(service.resume(id, room.getId()).simulatedAt()).isEqualTo(story.plusDays(3));
    }

    @Test void restPersistsOnlyInItsRoomAndSharedStateIsUntouched() {
        var room = fixture(false, 20, 70);
        var other = Conversation.fresh(room.getCharacter(), story);
        em.persist(other); em.flush();
        Long id = room.getCharacter().getId();
        service.configure(id, room.getId(), true);
        when(clock.now()).thenReturn(real.plusHours(1));
        var result = service.resume(id, room.getId());
        assertThat(result.lastReport().actions()).contains("휴식");
        em.flush(); em.clear();
        assertThat(rooms.findById(room.getId()).orElseThrow().getEnergy()).isEqualTo(50);
        assertThat(rooms.findById(other.getId()).orElseThrow().getEnergy()).isEqualTo(70);
        assertThat(em.createQuery("select s from CharacterState s where s.character.id = :id", CharacterState.class)
                .setParameter("id", id).getSingleResult().getEnergy()).isEqualTo(20);
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).hasSize(1);
        assertThat(memories.findForConversation(id, room.getId())).hasSize(1);
        assertThat(memories.findForConversation(id, other.getId())).isEmpty();
        assertThat(service.resume(id, room.getId()).lastReport().id()).isEqualTo(result.lastReport().id());
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).hasSize(1);
    }

    @Test void decisionRequiredPausesBeforeAnyActionAndDoesNotRepeatOnRefresh() {
        var room = fixture(true, 20, 10);
        Long id = room.getCharacter().getId();
        service.configure(id, room.getId(), true);
        when(clock.now()).thenReturn(real.plusHours(1));
        var result = service.resume(id, room.getId());
        assertThat(result.pauseReason()).isNotBlank();
        assertThat(result.simulatedAt()).isEqualTo(story);
        assertThat(result.lastReport().appliedSeconds()).isZero();
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).isEmpty();
        when(clock.now()).thenReturn(real.plusDays(1));
        assertThat(service.resume(id, room.getId()).lastReport().id()).isEqualTo(result.lastReport().id());
    }

    @Test void chatOperationExcludesCatchUpAndExpiredReplyCannotOverwriteNewOperation() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();
        service.configure(id, room.getId(), true);
        var operation = service.beginChat(id, room.getId());
        assertThatThrownBy(() -> service.resume(id, room.getId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(clock.now()).thenReturn(real.plusMinutes(11));
        var replacement = service.beginChat(id, room.getId());
        assertThatThrownBy(() -> service.applyChatTime(id, room.getId(), operation.token(), story.plusDays(99)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        service.endChat(id, room.getId(), operation.token());
        service.applyChatTime(id, room.getId(), replacement.token(), story.plusHours(1));
        service.completeChat(id, room.getId(), replacement.token(), "안녕", "안녕하세요", ContentMode.NORMAL);
        assertThat(service.resume(id, room.getId()).simulatedAt()).isEqualTo(story.plusHours(1));
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId()))
                .extracting(ChatMessage::getContent).containsExactly("안녕하세요", "안녕");
    }

    @Test void anotherCharacterCannotChangePresenceOrSetting() {
        var room = fixture(true, 70, 70);
        assertThatThrownBy(() -> service.configure(-1L, room.getId(), true))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.heartbeat(-1L, room.getId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
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

    @Test void dialogueStateIsRoomScopedPersistsAndTriggersExistingRestRule() {
        var room = fixture(false, 52, 70);
        Long id = room.getCharacter().getId();
        var other = Conversation.fresh(room.getCharacter(), story);
        em.persist(other); em.flush();
        var operation = service.beginChat(id, room.getId());
        var turn = new com.example.projectmm.domain.dialogue.DialogueTurn("*무거운 서류를 옮겨 지쳤다.*",
                new com.example.projectmm.domain.dialogue.DialogueStateChange(-6, 0, "무거운 서류를 옮겨 지쳤다", ""));
        var saved = service.completeChat(id, room.getId(), operation.token(), "서류 정리를 마쳤네요.", turn, ContentMode.NORMAL);
        assertThat(saved.stateUpdate().energy()).isEqualTo(46);
        em.flush(); em.clear();
        assertThat(service.current(id, room.getId()).energy()).isEqualTo(46);
        assertThat(service.current(id, other.getId()).energy()).isEqualTo(70);
        assertThat(em.createQuery("select s from CharacterState s where s.character.id = :id", CharacterState.class)
                .setParameter("id", id).getSingleResult().getEnergy()).isEqualTo(52);
        // 저장된 방 상태가 야간 캐치업의 입력으로 사용되어 실제 휴식이 이어져야 한다.
        service.configure(id, room.getId(), true);
        when(clock.now()).thenReturn(real.plusMinutes(2));
        var resumed = service.resume(id, room.getId());
        assertThat(resumed.energy()).isEqualTo(76);
        assertThat(resumed.lastReport().actions()).contains("휴식");
    }

    @Test void stateAndMessageApplyOnceAndInvalidProposalOnlyRejectsState() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();
        var first = service.beginChat(id, room.getId());
        var turn = new com.example.projectmm.domain.dialogue.DialogueTurn("*칭찬을 듣고 기뻐했다.*",
                new com.example.projectmm.domain.dialogue.DialogueStateChange(0, 4, "", "칭찬을 듣고 기뻐했다"));
        service.completeChat(id, room.getId(), first.token(), "잘했어요.", turn, ContentMode.NORMAL);
        assertThatThrownBy(() -> service.completeChat(id, room.getId(), first.token(), "잘했어요.", turn, ContentMode.NORMAL))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.current(id, room.getId()).mood()).isEqualTo(74);
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).hasSize(2);

        var second = service.beginChat(id, room.getId());
        var invalid = new com.example.projectmm.domain.dialogue.DialogueTurn("안녕하세요.",
                new com.example.projectmm.domain.dialogue.DialogueStateChange(-99, 10, "", "없는 근거"));
        var completed = service.completeChat(id, room.getId(), second.token(), "안녕", invalid, ContentMode.NORMAL);
        assertThat(completed.stateUpdate().status()).isEqualTo("REJECTED");
        assertThat(service.current(id, room.getId()).energy()).isEqualTo(70);
        assertThat(service.current(id, room.getId()).mood()).isEqualTo(74);
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).hasSize(4);
    }

    @Test void emptyReplyAndExpiredOperationCannotApplyState() {
        var room = fixture(true, 70, 70);
        Long id = room.getCharacter().getId();
        var operation = service.beginChat(id, room.getId());
        var change = new com.example.projectmm.domain.dialogue.DialogueStateChange(5, 0, "쉬었다", "");
        assertThatThrownBy(() -> service.completeChat(id, room.getId(), operation.token(), "안녕",
                new com.example.projectmm.domain.dialogue.DialogueTurn(" ", change), ContentMode.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);
        when(clock.now()).thenReturn(real.plusMinutes(11));
        assertThatThrownBy(() -> service.completeChat(id, room.getId(), operation.token(), "안녕",
                new com.example.projectmm.domain.dialogue.DialogueTurn("*쉬었다.*", change), ContentMode.NORMAL))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.current(id, room.getId()).energy()).isEqualTo(70);
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(room.getId())).isEmpty();
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
    void beginChatReportsCatchUpWhenAbsenceWasActuallyProcessed() {
        var room = fixture(true, 70, 70);
        Long characterId = room.getCharacter().getId();

        service.configure(
                characterId,
                room.getId(),
                true
        );

        when(clock.now())
                .thenReturn(real.plusMinutes(4));

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
    void continuationSavesOnlyCharacterMessageWithoutBlankUserMessage() {
        var room = fixture(true, 70, 70);
        Long characterId = room.getCharacter().getId();

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        var turn =
                new com.example.projectmm.domain.dialogue.DialogueTurn(
                        "*잠시 창밖을 바라보다가 다시 입을 열었다.*",
                        com.example.projectmm.domain.dialogue.DialogueStateChange.none()
                );

        var completed =
                service.completeChat(
                        characterId,
                        room.getId(),
                        operation.token(),
                        "",
                        turn,
                        ContentMode.NORMAL
                );

        assertThat(completed.source())
                .isNull();

        em.flush();
        em.clear();

        var savedMessages =
                messages.findTop20ByConversationIdOrderByCreatedAtDesc(
                        room.getId()
                );

        assertThat(savedMessages)
                .hasSize(1);

        assertThat(savedMessages.getFirst().getRole())
                .isEqualTo(ChatRole.CHARACTER);

        assertThat(savedMessages.getFirst().getContent())
                .isEqualTo(
                        "*잠시 창밖을 바라보다가 다시 입을 열었다.*"
                );

        assertThat(savedMessages)
                .noneMatch(message ->
                        message.getRole() == ChatRole.USER
                );
    }

    @Test
    void dialogueRelationshipChangeAppliesToExistingRelationship() {

        var room = fixture(true, 70, 70);
        Long characterId =
                room.getCharacter().getId();

        var target =
                new Character("관계 대상");

        em.persist(target);

        var relationship =
                new com.example.projectmm.domain.relationship.Relationship(
                        room.getCharacter(),
                        target
                );

        em.persist(relationship);
        em.flush();

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        var turn =
                new com.example.projectmm.domain.dialogue.DialogueTurn(
                        "*상대를 조금 더 신뢰하게 되었다.*",
                        com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                        com.example.projectmm.domain.dialogue.DialoguePlayerStateChange.none(),
                        java.util.List.of(),
                        new com.example.projectmm.domain.dialogue.DialogueRelationshipChange(
                                target.getId(),
                                new com.example.projectmm.domain.relationship.RelationshipDelta(
                                        3,
                                        2,
                                        -1
                                )
                        )
                );

        service.completeChat(
                characterId,
                room.getId(),
                operation.token(),
                "약속을 지켰어요.",
                turn,
                ContentMode.NORMAL
        );

        em.flush();
        em.clear();

        var saved =
                em.find(
                        com.example.projectmm.domain.relationship.Relationship.class,
                        relationship.getId()
                );

        assertThat(saved.getAffinity())
                .isEqualTo(3);

        assertThat(saved.getTrust())
                .isEqualTo(2);

        assertThat(saved.getTension())
                .isEqualTo(0);
    }

    @Test
    void dialogueRelationshipChangeIgnoresUnknownTargetWithoutDiscardingChat() {

        var room = fixture(true, 70, 70);
        Long characterId =
                room.getCharacter().getId();

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        var turn =
                new com.example.projectmm.domain.dialogue.DialogueTurn(
                        "*고개를 끄덕였다.*",
                        com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                        com.example.projectmm.domain.dialogue.DialoguePlayerStateChange.none(),
                        java.util.List.of(),
                        new com.example.projectmm.domain.dialogue.DialogueRelationshipChange(
                                999999L,
                                new com.example.projectmm.domain.relationship.RelationshipDelta(
                                        5,
                                        5,
                                        5
                                )
                        )
                );

        service.completeChat(
                characterId,
                room.getId(),
                operation.token(),
                "안녕하세요.",
                turn,
                ContentMode.NORMAL
        );

        em.flush();
        em.clear();

        var savedMessages =
                messages.findTop20ByConversationIdOrderByCreatedAtDesc(
                        room.getId()
                );

        assertThat(savedMessages)
                .hasSize(2);

        assertThat(savedMessages)
                .anyMatch(message ->
                        message.getRole() == ChatRole.CHARACTER
                                && message.getContent()
                                .equals("*고개를 끄덕였다.*")
                );
    }

    @Test
    void dialogueRelationshipFactPersistsWithCharacterMessageProvenance() {

        var room = fixture(true, 70, 70);
        Long characterId =
                room.getCharacter().getId();

        var target =
                new Character("관계 대상");

        em.persist(target);

        var relationship =
                new com.example.projectmm.domain.relationship.Relationship(
                        room.getCharacter(),
                        target
                );

        em.persist(relationship);
        em.flush();

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        var turn =
                new com.example.projectmm.domain.dialogue.DialogueTurn(
                        "우리 이제 부부예요.",
                        com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                        com.example.projectmm.domain.dialogue.DialoguePlayerStateChange.none(),
                        java.util.List.of(),
                        com.example.projectmm.domain.dialogue.DialogueRelationshipChange.none(),
                        new com.example.projectmm.domain.dialogue.DialogueRelationshipFactChange(
                                target.getId(),
                                "relationship_status",
                                "spouse"
                        )
                );

        service.completeChat(
                characterId,
                room.getId(),
                operation.token(),
                "그럼 이제 우리 부부인 거죠?",
                turn,
                ContentMode.NORMAL
        );

        em.flush();
        em.clear();

        var fact =
                em.createQuery(
                                """
                                select f
                                from RelationshipFact f
                                where f.fromCharacter.id = :fromId
                                  and f.toCharacter.id = :toId
                                  and f.relation = :relation
                                """,
                                com.example.projectmm.domain.relationship.RelationshipFact.class
                        )
                        .setParameter("fromId", characterId)
                        .setParameter("toId", target.getId())
                        .setParameter("relation", "relationship_status")
                        .getSingleResult();

        assertThat(fact.getValue())
                .isEqualTo("spouse");

        assertThat(fact.getSourceEvent())
                .isNull();

        assertThat(fact.getSourceChatMessage())
                .isNotNull();

        assertThat(fact.getSourceChatMessage().getRole())
                .isEqualTo(ChatRole.CHARACTER);

        assertThat(fact.getSourceChatMessage().getContent())
                .isEqualTo("우리 이제 부부예요.");

        assertThat(fact.getEstablishedAt())
                .isEqualTo(real);
    }

    @Test
    void dialogueRelationshipFactIgnoresUnknownRelationshipWithoutDiscardingChat() {

        var room = fixture(true, 70, 70);
        Long characterId =
                room.getCharacter().getId();

        var unrelatedTarget =
                new Character("관계 없는 대상");

        em.persist(unrelatedTarget);
        em.flush();

        var operation =
                service.beginChat(
                        characterId,
                        room.getId()
                );

        var turn =
                new com.example.projectmm.domain.dialogue.DialogueTurn(
                        "*고개를 끄덕였다.*",
                        com.example.projectmm.domain.dialogue.DialogueStateChange.none(),
                        com.example.projectmm.domain.dialogue.DialoguePlayerStateChange.none(),
                        java.util.List.of(),
                        com.example.projectmm.domain.dialogue.DialogueRelationshipChange.none(),
                        new com.example.projectmm.domain.dialogue.DialogueRelationshipFactChange(
                                unrelatedTarget.getId(),
                                "relationship_status",
                                "spouse"
                        )
                );

        service.completeChat(
                characterId,
                room.getId(),
                operation.token(),
                "안녕하세요.",
                turn,
                ContentMode.NORMAL
        );

        em.flush();
        em.clear();

        Long factCount =
                em.createQuery(
                                """
                                select count(f)
                                from RelationshipFact f
                                where f.fromCharacter.id = :fromId
                                  and f.toCharacter.id = :toId
                                """,
                                Long.class
                        )
                        .setParameter("fromId", characterId)
                        .setParameter("toId", unrelatedTarget.getId())
                        .getSingleResult();

        assertThat(factCount)
                .isZero();

        var savedMessages =
                messages.findTop20ByConversationIdOrderByCreatedAtDesc(
                        room.getId()
                );

        assertThat(savedMessages)
                .hasSize(2);

        assertThat(savedMessages)
                .anyMatch(message ->
                        message.getRole() == ChatRole.CHARACTER
                                && message.getContent()
                                .equals("*고개를 끄덕였다.*")
                );
    }

    private Conversation fixture(boolean independent, int energy, int mood) {
        var character = new Character("자동 행동 QA");
        em.persist(character);
        em.persist(new CharacterState(character, energy, mood, story));
        var profile = new PlayerProfile(1, "하린", "사서", null);
        em.persist(profile);
        var room = independent ? Conversation.fresh(character, story) : new Conversation(character, story);
        if (independent) room.applyRoomState(energy, mood, story);
        room.selectPlayerProfile(profile);
        em.persist(room); em.flush();
        return room;
    }


}
