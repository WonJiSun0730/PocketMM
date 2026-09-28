package com.example.projectmm.application.chat;

import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.simulation.*;
import com.example.projectmm.application.state.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.state.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({ConversationProgressService.class, CustomStateProposalService.class, CustomStateService.class,
        CatchUpSimulationService.class, CatchUpSegmentService.class, CatchUpEventCandidateService.class,
        EventCandidateValidator.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomStateChatCompletionTest {
    @Autowired ConversationProgressService progress;
    @Autowired CustomStateService custom;
    @Autowired CustomStateProposalService proposals;
    @Autowired CustomStateValueRepository values;
    @Autowired ConversationRepository rooms;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean ChatMessageRepository messages;
    @MockitoBean PresenceClock clock;
    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 12, 0);
    private Long characterId, roomId, otherRoomId, firstId, secondId, foreignId, otherProfileId;
    private String token;

    @BeforeEach void setup() {
        when(clock.now()).thenReturn(now);
        tx().executeWithoutResult(status -> {
            var character = new Character("NPC");
            var profile = new PlayerProfile(1, "Player", "설정", null);
            var other = new PlayerProfile(2, "Other", "설정", null);
            em.persist(character); em.persist(profile); em.persist(other);
            characterId = character.getId(); otherProfileId = other.getId();
            em.persist(new CharacterState(character, 70, 70, now));
            roomId = room(character, profile);
            otherRoomId = room(character, profile);
            firstId = definition(profile, "첫 상태");
            secondId = definition(profile, "둘째 상태");
            foreignId = definition(other, "타인 상태");
        });
        token = progress.beginChat(characterId, roomId).token();
    }

    @AfterEach void cleanup() {
        reset(messages);
        tx().executeWithoutResult(status -> {
            em.createQuery("delete from ChatMessage").executeUpdate();
            em.createQuery("delete from CustomStateValue").executeUpdate();
            em.createQuery("delete from CustomStateDefinition").executeUpdate();
            em.createQuery("delete from Conversation").executeUpdate();
            em.createQuery("delete from CharacterState").executeUpdate();
            em.createQuery("delete from PlayerProfile").executeUpdate();
            em.createQuery("delete from Character").executeUpdate();
        });
    }

    @Test void emptyProposalsCommitExistingChatWithoutInitializingValues() {
        assertThat(complete(List.of()).customStateStatus()).isEqualTo(CustomStateProposalService.ApplyStatus.NONE);
        assertCommittedChat();
        assertThat(values.count()).isZero();
    }

    @Test void validBatchCommitsMessagesNpcAndCustomTogetherAndIsolatesRooms() {
        custom.initialize(roomId); custom.initialize(otherRoomId);
        assertThat(complete(List.of(proposal(firstId, -5), proposal(secondId, 10))).customStateStatus())
                .isEqualTo(CustomStateProposalService.ApplyStatus.APPLIED);
        assertCommittedChat();
        assertThat(current(firstId)).isEqualTo(62);
        assertThat(current(secondId)).isEqualTo(77);
        assertThat(custom.current(otherRoomId)).extracting(CustomStateSnapshot::currentValue).containsExactly(67.0, 67.0);
    }

    @Test void refreshesStaleManagedValueAfterUserEditCommittedDuringGeneration() {
        custom.initialize(roomId);
        var oldSnapshot = custom.current(roomId);
        tx().executeWithoutResult(status -> {
            var stale = values.findByConversationIdAndDefinitionId(roomId, firstId).orElseThrow();
            var independent = tx();
            independent.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
            independent.executeWithoutResult(edit -> custom.updateByUser(roomId, firstId, 80));
            assertThat(stale.getCurrentValue()).isEqualTo(67);
            assertThat(complete(List.of(proposal(firstId, -5))).customStateStatus())
                    .isEqualTo(CustomStateProposalService.ApplyStatus.APPLIED);
        });
        assertThat(oldSnapshot.getFirst().currentValue()).isEqualTo(67);
        assertThat(current(firstId)).isEqualTo(75);
        assertCommittedChat();
    }

    @ParameterizedTest
    @ValueSource(strings = {"range", "foreign", "missingDefinition", "missingValue", "profile", "duplicate", "nonFinite"})
    void expectedRejectionPreservesWholeCustomBatchAndCommitsChat(String failure) {
        custom.initialize(roomId);
        List<CustomStateChangeProposal> batch = List.of(proposal(firstId, -5), proposal(secondId, 40));
        switch (failure) {
            case "range" -> custom.updateByUser(roomId, secondId, 90);
            case "foreign" -> batch = List.of(proposal(firstId, -5), proposal(foreignId, 1));
            case "missingDefinition" -> batch = List.of(proposal(firstId, -5), proposal(Long.MAX_VALUE, 1));
            case "missingValue" -> tx().executeWithoutResult(s -> values.delete(
                    values.findByConversationIdAndDefinitionId(roomId, secondId).orElseThrow()));
            case "profile" -> tx().executeWithoutResult(s -> em.find(Conversation.class, roomId)
                    .selectPlayerProfile(em.find(PlayerProfile.class, otherProfileId)));
            case "duplicate" -> batch = List.of(proposal(firstId, -5), proposal(firstId, -5));
            case "nonFinite" -> batch = List.of(proposal(firstId, -5), proposal(secondId, Double.NaN));
        }
        var before = custom.current(roomId);
        assertThat(complete(batch).customStateStatus()).isEqualTo(CustomStateProposalService.ApplyStatus.REJECTED);
        assertThat(custom.current(roomId)).isEqualTo(before);
        assertCommittedChat();
    }

    @Test void failureSavingCharacterMessageRollsBackAlreadyFlushedCustomAndUserMessage() {
        custom.initialize(roomId);
        // USER 저장 뒤 CHARACTER 저장 실패를 주입한다. 실제 트랜잭션/DB 저장은 유지한다.
        doAnswer(call -> {
            assertThat(current(firstId)).isEqualTo(62);
            assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(roomId)).hasSize(1);
            throw new IllegalStateException("메시지 저장 실패 주입");
        }).when(messages).save(argThat((ChatMessage message) -> message.getRole() == ChatRole.CHARACTER));
        assertThatThrownBy(() -> complete(List.of(proposal(firstId, -5))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("실패 주입");
        assertThat(current(firstId)).isEqualTo(67);
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(roomId)).isEmpty();
        var room = rooms.findById(roomId).orElseThrow();
        assertThat(room.getEnergy()).isEqualTo(70);
        assertThat(room.getChatOperationId()).isEqualTo(token);
    }

    @Test void sameCompletedTokenCannotApplyDeltaTwice() {
        custom.initialize(roomId);
        complete(List.of(proposal(firstId, -5)));
        assertThatThrownBy(() -> complete(List.of(proposal(firstId, -5))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(current(firstId)).isEqualTo(62);
        assertCommittedChat();
    }

    @Test void chatApplyCannotCreateItsOwnIndependentTransaction() {
        assertThatThrownBy(() -> proposals.applyForChat(roomId, List.of(proposal(firstId, -5))))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(values.count()).isZero();
    }

    @Test void userEditRoomLockSerializesConcurrentChatCompletion() throws Exception {
        custom.initialize(roomId);
        var edited = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var applying = new java.util.concurrent.CountDownLatch(1);
        // 서로 다른 커넥션으로 사용자 수정의 room 잠금이 완료 처리를 막는지 확인한다.
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var edit = executor.submit(() -> tx().executeWithoutResult(status -> {
                custom.updateByUser(roomId, firstId, 80);
                em.flush();
                edited.countDown();
                await(release);
            }));
            try {
                assertThat(edited.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var completion = executor.submit(() -> {
                    applying.countDown();
                    return complete(List.of(proposal(firstId, -5)));
                });
                assertThat(applying.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> completion.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown();
                edit.get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(completion.get(5, java.util.concurrent.TimeUnit.SECONDS).customStateStatus())
                        .isEqualTo(CustomStateProposalService.ApplyStatus.APPLIED);
            } finally {
                release.countDown();
            }
        }
        assertThat(current(firstId)).isEqualTo(75);
        assertCommittedChat();
    }

    private void await(java.util.concurrent.CountDownLatch latch) {
        try {
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("잠금 테스트 대기 초과");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private ConversationProgressService.CompletedChat complete(List<CustomStateChangeProposal> changes) {
        return progress.completeChat(characterId, roomId, token, "쉬세요",
                new DialogueTurn("*쉬었다.*", new DialogueStateChange(2, 0, "쉬었다", ""), changes), ContentMode.NORMAL);
    }

    private void assertCommittedChat() {
        // 서비스 반환 후 별도 조회로 실제 커밋을 확인한다(테스트 자체의 롤백 트랜잭션 없음).
        assertThat(messages.findTop20ByConversationIdOrderByCreatedAtDesc(roomId))
                .extracting(ChatMessage::getContent).containsExactly("*쉬었다.*", "쉬세요");
        var room = rooms.findById(roomId).orElseThrow();
        assertThat(room.getEnergy()).isEqualTo(72);
        assertThat(room.getMood()).isEqualTo(70);
        assertThat(room.getChatOperationId()).isNull();
    }

    private double current(Long definitionId) {
        return values.findByConversationIdAndDefinitionId(roomId, definitionId).orElseThrow().getCurrentValue();
    }
    private CustomStateChangeProposal proposal(Long id, double delta) { return new CustomStateChangeProposal(id, delta); }
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private Long room(Character character, PlayerProfile profile) {
        var room = Conversation.fresh(character, now);
        room.selectPlayerProfile(profile); em.persist(room); return room.getId();
    }
    private Long definition(PlayerProfile profile, String name) {
        var definition = new CustomStateDefinition(profile, name, 0, 100, 67, null, null);
        em.persist(definition); return definition.getId();
    }
}
