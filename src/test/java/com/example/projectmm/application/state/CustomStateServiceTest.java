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

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import(CustomStateService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomStateServiceTest {
    @Autowired CustomStateService service;
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
    void initializesBothDefinitionsAndReturnsMetadata() {
        service.initialize(roomA);
        assertThat(service.current(roomA)).containsExactly(
                new CustomStateSnapshot(pieta, "피에타", 100, 0, 100, "%", "신앙"),
                new CustomStateSnapshot(mana, "마력", 50.5, -10, 200, "MP", "마법 자원"));
        assertThat(values.count()).isEqualTo(2);
    }

    @Test
    void repeatedInitializationOnlyCreatesMissingValuesAndPreservesEdits() {
        service.updateByUser(roomA, pieta, 67);
        service.initialize(roomA);
        service.initialize(roomA);
        assertThat(values.count()).isEqualTo(2);
        assertThat(current(roomA, pieta)).isEqualTo(67);
        assertThat(current(roomA, mana)).isEqualTo(50.5);
    }

    @Test
    void editsRemainIndependentAcrossRoomsAndDefinitions() {
        service.initialize(roomA);
        service.initialize(roomB);
        service.updateByUser(roomA, pieta, 67);
        assertThat(current(roomA, pieta)).isEqualTo(67);
        assertThat(current(roomB, pieta)).isEqualTo(100);
        assertThat(current(roomA, mana)).isEqualTo(50.5);
        assertThat(current(roomB, mana)).isEqualTo(50.5);
    }

    @Test
    void successiveUserEditsCommitLatestValue() {
        service.updateByUser(roomA, pieta, 67);
        assertThat(service.updateByUser(roomA, pieta, 80).currentValue()).isEqualTo(80);
        assertThat(current(roomA, pieta)).isEqualTo(80);
        assertThat(values.findByConversationIdAndDefinitionId(roomA, pieta).orElseThrow().getCurrentValue())
                .isEqualTo(80);
    }

    @Test
    void rejectsOtherProfileDefinitionWithoutChangingExistingState() {
        service.initialize(roomA);
        var before = service.current(roomA);
        assertStatus(() -> service.updateByUser(roomA, foreignDefinition, 10), HttpStatus.BAD_REQUEST);
        assertThat(service.current(roomA)).isEqualTo(before);
        assertThat(values.findByConversationIdAndDefinitionId(roomA, foreignDefinition)).isEmpty();
    }

    @Test
    void missingProfileRejectsInitializationAndEditing() {
        assertStatus(() -> service.initialize(noProfileRoom), HttpStatus.CONFLICT);
        assertStatus(() -> service.updateByUser(noProfileRoom, pieta, 67), HttpStatus.CONFLICT);
        assertThat(service.current(noProfileRoom)).isEmpty();
        assertThat(values.count()).isZero();
    }

    @Test
    void missingConversationOrDefinitionIsNotFound() {
        assertStatus(() -> service.initialize(-1L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.current(-1L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.updateByUser(-1L, pieta, 67), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.updateByUser(roomA, -1L, 67), HttpStatus.NOT_FOUND);
        assertThat(values.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidEditsPreserveCommittedValuesAndDoNotCreateMissingValues(double invalid) {
        service.updateByUser(roomA, pieta, 67);
        assertThatIllegalArgumentException().isThrownBy(() -> service.updateByUser(roomA, pieta, invalid));
        assertThat(current(roomA, pieta)).isEqualTo(67);
        assertThatIllegalArgumentException().isThrownBy(() -> service.updateByUser(roomB, pieta, invalid));
        assertThat(service.current(roomB)).isEmpty();
        assertThat(values.count()).isEqualTo(1);
    }

    @Test
    void readingDoesNotInitializeValues() {
        assertThat(service.current(roomA)).isEmpty();
        assertThat(values.count()).isZero();
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
