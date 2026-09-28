package com.example.projectmm.application.dialogue;

import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.policy.ContentMode;
import com.example.projectmm.application.policy.ContentModePolicy;
import com.example.projectmm.application.state.CustomStateService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.dialogue.DialogueContext;
import com.example.projectmm.domain.dialogue.DialogueCustomState;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.state.CustomStateDefinition;
import com.example.projectmm.domain.state.CustomStateValueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({DialogueContextBuilder.class, CustomStateService.class, ContentModePolicy.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DialogueCustomStateIntegrationTest {
    @Autowired DialogueContextBuilder builder;
    @Autowired CustomStateService service;
    @Autowired CustomStateValueRepository values;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean MemoryRecallService memories;
    private final LocalDateTime storyTime = LocalDateTime.of(2030, 1, 1, 12, 0);
    private Long characterId;
    private Long roomA;
    private Long roomB;
    private Long pieta;
    private Long mana;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var profile = new PlayerProfile(1, "Player", "Description", null);
            var character = new Character("NPC");
            em.persist(profile);
            em.persist(character);
            characterId = character.getId();
            em.persist(new CharacterState(character, 40, 50, storyTime));
            roomA = room(character, profile);
            roomB = room(character, profile);
            var first = new CustomStateDefinition(profile, "피에타", 0, 100, 100, "%", "신앙");
            var second = new CustomStateDefinition(profile, "마력", -10, 200, 50.5, "MP", "마법 자원");
            em.persist(first);
            em.persist(second);
            pieta = first.getId();
            mana = second.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            em.createQuery("delete from CustomStateValue").executeUpdate();
            em.createQuery("delete from CustomStateDefinition").executeUpdate();
            em.createQuery("delete from Conversation").executeUpdate();
            em.createQuery("delete from CharacterState").executeUpdate();
            em.createQuery("delete from PlayerProfile").executeUpdate();
            em.createQuery("delete from Character").executeUpdate();
        });
    }

    @Test
    void readsEachStoredValueFromOnlyTheRequestedRoomWithoutChangingCoreContext() {
        service.updateByUser(roomA, pieta, 67);
        service.updateByUser(roomA, mana, -5.5);
        service.updateByUser(roomB, pieta, 12);
        service.updateByUser(roomB, mana, 150);
        var context = build(roomA);
        assertThat(context.customStates()).containsExactly(
                new DialogueCustomState(pieta, "피에타", 67, 0, 100, "%", "신앙"),
                new DialogueCustomState(mana, "마력", -5.5, -10, 200, "MP", "마법 자원"));
        assertThat(build(roomB).customStates()).extracting(DialogueCustomState::currentValue)
                .containsExactly(12.0, 150.0);
        assertThat(context.energy()).isEqualTo(70);
        assertThat(context.mood()).isEqualTo(70);
        assertThat(context.simulatedAt()).isEqualTo(storyTime);
        assertThat(context.playerProfile().name()).isEqualTo("Player");
        assertThat(values.count()).isEqualTo(4);
    }

    @Test
    void newContextReadsCommittedUserEditInsteadOfPreviousSnapshotOrDefault() {
        service.updateByUser(roomA, pieta, 67);
        var previous = build(roomA);
        service.updateByUser(roomA, pieta, 80);
        assertThat(previous.customStates().getFirst().currentValue()).isEqualTo(67);
        assertThat(build(roomA).customStates().getFirst().currentValue()).isEqualTo(80);
        assertThat(values.findByConversationIdAndDefinitionId(roomA, pieta).orElseThrow().getCurrentValue())
                .isEqualTo(80);
        // 마력 Definition이 있어도 Value가 없으면 Context에 채우지 않는다.
        assertThat(build(roomA).customStates()).hasSize(1);
        assertThat(values.count()).isEqualTo(1);
    }

    @Test
    void emptyRoomProducesEmptyListEvenWhenProfileHasDefinitionsAndOtherRoomHasValues() {
        service.initialize(roomB);
        assertThat(build(roomA).customStates()).isEmpty();
        assertThat(build(roomA).customStates()).isEmpty();
        assertThat(values.findByConversationIdOrderByIdAsc(roomA)).isEmpty();
        assertThat(values.count()).isEqualTo(2);
    }

    private Long room(Character character, PlayerProfile profile) {
        var room = Conversation.fresh(character, storyTime);
        room.selectPlayerProfile(profile);
        em.persist(room);
        return room.getId();
    }

    private DialogueContext build(Long roomId) {
        return builder.build(characterId, roomId, ContentMode.NORMAL, "현재 상태는?");
    }
}
