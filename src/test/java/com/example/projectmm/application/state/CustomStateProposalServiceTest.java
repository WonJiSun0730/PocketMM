package com.example.projectmm.application.state;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.state.CustomStateDefinition;
import com.example.projectmm.domain.state.CustomStateValueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import com.example.projectmm.domain.state.CustomStateChangeProposal;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({CustomStateService.class, CustomStateProposalService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomStateProposalServiceTest {
    @Autowired CustomStateService service;
    @Autowired CustomStateProposalService proposals;
    @Autowired CustomStateValueRepository values;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    private Long roomA;
    private Long roomB;
    private Long noProfileRoom;
    private Long pieta;
    private Long mana;
    private Long foreignDefinition;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var profile = new PlayerProfile(1, "Player", "Description", null);
            var other = new PlayerProfile(2, "Other", "Description", null);
            var character = new Character("NPC");
            em.persist(profile);
            em.persist(other);
            em.persist(character);
            roomA = room(character, profile);
            roomB = room(character, profile);
            noProfileRoom = room(character, null);
            var first = new CustomStateDefinition(profile, "피에타", 0, 100, 100, "%", "신앙");
            var second = new CustomStateDefinition(profile, "마력", -10, 200, 50.5, "MP", "마법 자원");
            var foreign = new CustomStateDefinition(other, "중독도", 0, 100, 0, null, null);
            em.persist(first);
            em.persist(second);
            em.persist(foreign);
            pieta = first.getId();
            mana = second.getId();
            foreignDefinition = foreign.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            em.createQuery("delete from CustomStateValue").executeUpdate();
            em.createQuery("delete from CustomStateDefinition").executeUpdate();
            em.createQuery("delete from Conversation").executeUpdate();
            em.createQuery("delete from PlayerProfile").executeUpdate();
            em.createQuery("delete from Character").executeUpdate();
        });
    }

    @Test
    void appliesValidBatchFromPersistedValuesAndIsolatesRooms() {
        service.initialize(roomA);
        service.initialize(roomB);
        var result = proposals.apply(roomA, List.of(new CustomStateChangeProposal(pieta, -20.0),
                new CustomStateChangeProposal(mana, 0.25)));
        assertThat(result).extracting(CustomStateSnapshot::currentValue).containsExactly(80.0, 50.75);
        assertThat(current(roomA, pieta)).isEqualTo(80);
        assertThat(current(roomB, pieta)).isEqualTo(100);
        assertThat(current(roomB, mana)).isEqualTo(50.5);
    }

    @Test
    void usesUserEditedLatestValueInsteadOfOldSnapshot() {
        var oldSnapshot = service.updateByUser(roomA, pieta, 67);
        var proposal = new CustomStateChangeProposal(pieta, -5.0);
        service.updateByUser(roomA, pieta, 80);
        assertThat(oldSnapshot.currentValue()).isEqualTo(67);
        assertThat(proposals.apply(roomA, List.of(proposal)).getFirst().currentValue()).isEqualTo(75);
        assertThat(current(roomA, pieta)).isEqualTo(75);
        // 제안 뒤에 도착한 사용자 수정도 다시 우선한다.
        service.updateByUser(roomA, pieta, 90);
        assertThat(current(roomA, pieta)).isEqualTo(90);
    }

    @Test
    void refreshesStaleManagedValueAfterCommittedUserEdit() {
        service.updateByUser(roomA, pieta, 67);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var stale = values.findByConversationIdAndDefinitionId(roomA, pieta).orElseThrow();
            assertThat(stale.getCurrentValue()).isEqualTo(67);
            var independent = new TransactionTemplate(transactions);
            independent.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
            independent.executeWithoutResult(other -> service.updateByUser(roomA, pieta, 80));
            assertThat(stale.getCurrentValue()).isEqualTo(67);
            assertThat(proposals.apply(roomA, List.of(new CustomStateChangeProposal(pieta, -5.0)))
                    .getFirst().currentValue()).isEqualTo(75);
        });
        assertThat(current(roomA, pieta)).isEqualTo(75);
    }

    @Test
    void acceptsExactMinMaxAndZeroDelta() {
        service.initialize(roomA);
        proposals.apply(roomA, List.of(new CustomStateChangeProposal(mana, -60.5)));
        assertThat(current(roomA, mana)).isEqualTo(-10);
        proposals.apply(roomA, List.of(new CustomStateChangeProposal(mana, 210.0)));
        assertThat(current(roomA, mana)).isEqualTo(200);
        proposals.apply(roomA, List.of(new CustomStateChangeProposal(mana, 0.0)));
        assertThat(current(roomA, mana)).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-81, 21, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidNumbersWithoutPartialBatchWrites(double delta) {
        service.initialize(roomA);
        service.updateByUser(roomA, pieta, 80);
        var before = service.current(roomA);
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.apply(roomA, List.of(
                new CustomStateChangeProposal(mana, 10.0), new CustomStateChangeProposal(pieta, delta))));
        assertThat(service.current(roomA)).isEqualTo(before);
    }

    @Test
    void rejectsForeignAndMissingDefinitionsWithoutPartialWrites() {
        service.initialize(roomA);
        var before = service.current(roomA);
        assertStatus(() -> proposals.apply(roomA, List.of(new CustomStateChangeProposal(pieta, -5.0),
                new CustomStateChangeProposal(foreignDefinition, 1.0))), HttpStatus.BAD_REQUEST);
        assertStatus(() -> proposals.apply(roomA, List.of(new CustomStateChangeProposal(pieta, -5.0),
                new CustomStateChangeProposal(Long.MAX_VALUE, 1.0))), HttpStatus.NOT_FOUND);
        assertThat(service.current(roomA)).isEqualTo(before);
        assertThat(values.count()).isEqualTo(2);
    }

    @Test
    void rejectsStructurallyInvalidBatchesWithoutWrites() {
        service.initialize(roomA);
        var before = service.current(roomA);
        List<List<CustomStateChangeProposal>> invalid = java.util.Arrays.asList(null,
                java.util.Arrays.asList(new CustomStateChangeProposal(pieta, -5.0), null),
                List.of(new CustomStateChangeProposal(null, 1.0)),
                List.of(new CustomStateChangeProposal(-1L, 1.0)),
                List.of(new CustomStateChangeProposal(pieta, null)),
                List.of(new CustomStateChangeProposal(pieta, -1.0), new CustomStateChangeProposal(pieta, -2.0)));
        for (var batch : invalid) {
            assertThatIllegalArgumentException().isThrownBy(() -> proposals.apply(roomA, batch));
            assertThat(service.current(roomA)).isEqualTo(before);
        }
        assertThat(proposals.apply(roomA, List.of())).isEmpty();
        assertThat(service.current(roomA)).isEqualTo(before);
    }

    @Test
    void doesNotCreateValuesOrApplyToMissingProfileOrConversation() {
        assertStatus(() -> proposals.apply(roomA, List.of(new CustomStateChangeProposal(pieta, -5.0))), HttpStatus.CONFLICT);
        assertStatus(() -> proposals.apply(noProfileRoom, List.of(new CustomStateChangeProposal(pieta, -5.0))), HttpStatus.CONFLICT);
        assertStatus(() -> proposals.apply(Long.MAX_VALUE, List.of(new CustomStateChangeProposal(pieta, -5.0))), HttpStatus.NOT_FOUND);
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.apply(null, List.of()));
        assertThat(values.count()).isZero();
    }

    @Test
    void checksCurrentlySelectedProfileRatherThanOldOwnership() {
        service.initialize(roomA);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var other = em.find(CustomStateDefinition.class, foreignDefinition).getPlayerProfile();
            em.find(Conversation.class, roomA).selectPlayerProfile(other);
        });
        var before = service.current(roomA);
        assertStatus(() -> proposals.apply(roomA, List.of(new CustomStateChangeProposal(pieta, -5.0))), HttpStatus.BAD_REQUEST);
        assertThat(service.current(roomA)).isEqualTo(before);
    }

    @Test
    void finiteDeltaOverflowDoesNotChangeDatabase() {
        Long extreme = new TransactionTemplate(transactions).execute(status -> {
            var profile = em.find(Conversation.class, roomA).getPlayerProfile();
            var definition = new CustomStateDefinition(profile, "큰 값", 0, Double.MAX_VALUE, Double.MAX_VALUE, null, null);
            em.persist(definition);
            return definition.getId();
        });
        service.initialize(roomA);
        var before = service.current(roomA);
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.apply(roomA, List.of(
                new CustomStateChangeProposal(pieta, -5.0), new CustomStateChangeProposal(extreme, Double.MAX_VALUE))));
        assertThat(service.current(roomA)).isEqualTo(before);
    }

    private Long room(Character character, PlayerProfile profile) {
        var room = new Conversation(character, LocalDateTime.of(2030, 1, 1, 12, 0));
        room.selectPlayerProfile(profile);
        em.persist(room);
        return room.getId();
    }

    private double current(Long roomId, Long definitionId) {
        return service.current(roomId).stream().filter(state -> state.definitionId().equals(definitionId))
                .findFirst().orElseThrow().currentValue();
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
