package com.example.projectmm.domain.state;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.player.PlayerProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
class CustomStateValueRepositoryTest {
    @Autowired CustomStateValueRepository values;
    @Autowired EntityManager em;
    private Conversation roomA;
    private Conversation roomB;
    private CustomStateDefinition pieta;
    private CustomStateDefinition mana;

    @BeforeEach
    void setUp() {
        var profile = new PlayerProfile(1, "Player", "Description", null);
        var character = new Character("NPC");
        em.persist(profile);
        em.persist(character);
        roomA = new Conversation(character, LocalDateTime.of(2030, 1, 1, 12, 0));
        roomB = new Conversation(character, LocalDateTime.of(2030, 1, 1, 12, 0));
        roomA.selectPlayerProfile(profile);
        roomB.selectPlayerProfile(profile);
        em.persist(roomA);
        em.persist(roomB);
        pieta = new CustomStateDefinition(profile, "피에타", 0, 100, 100, "%", "신앙");
        mana = new CustomStateDefinition(profile, "마력", -10, 200, 50.5, null, null);
        em.persist(pieta);
        em.persist(mana);
    }

    @Test
    void changingOneRoomDoesNotChangeAnotherRoomSharingProfileAndDefinition() {
        values.save(new CustomStateValue(roomA, pieta));
        values.save(new CustomStateValue(roomB, pieta));
        em.flush();
        em.clear();
        assertThat(load(roomA, pieta).getCurrentValue()).isEqualTo(100);
        assertThat(load(roomB, pieta).getCurrentValue()).isEqualTo(100);

        load(roomA, pieta).updateCurrentValue(67);
        em.flush();
        em.clear();

        assertThat(load(roomA, pieta).getCurrentValue()).isEqualTo(67);
        assertThat(load(roomB, pieta).getCurrentValue()).isEqualTo(100);
        assertThat(load(roomA, pieta).getDefinition().getDefaultValue()).isEqualTo(100);
        assertThat(values.findByConversationIdOrderByIdAsc(roomA.getId()))
                .extracting(CustomStateValue::getCurrentValue).containsExactly(67.0);
        assertThat(values.findByConversationIdOrderByIdAsc(roomB.getId()))
                .extracting(CustomStateValue::getCurrentValue).containsExactly(100.0);
    }

    @Test
    void differentDefinitionsInSameRoomKeepIndependentValues() {
        values.save(new CustomStateValue(roomA, pieta));
        values.save(new CustomStateValue(roomA, mana));
        em.flush();
        em.clear();

        load(roomA, pieta).updateCurrentValue(67);
        em.flush();
        em.clear();
        assertThat(load(roomA, pieta).getCurrentValue()).isEqualTo(67);
        assertThat(load(roomA, mana).getCurrentValue()).isEqualTo(50.5);

        load(roomA, mana).updateCurrentValue(-5.5);
        em.flush();
        em.clear();
        assertThat(values.findByConversationIdOrderByIdAsc(roomA.getId()))
                .extracting(CustomStateValue::getCurrentValue).containsExactly(67.0, -5.5);
        assertThat(values.findByConversationIdOrderByIdAsc(roomB.getId())).isEmpty();
        assertThat(values.findByConversationIdAndDefinitionId(roomB.getId(), mana.getId())).isEmpty();
    }

    @Test
    void databaseRejectsDuplicateConversationAndDefinition() {
        values.saveAndFlush(new CustomStateValue(roomA, pieta));
        assertThatThrownBy(() -> values.saveAndFlush(new CustomStateValue(roomA, pieta)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("UK_CUSTOM_STATE_VALUE_CONVERSATION_DEFINITION");
    }

    private CustomStateValue load(Conversation room, CustomStateDefinition definition) {
        return values.findByConversationIdAndDefinitionId(room.getId(), definition.getId()).orElseThrow();
    }
}
