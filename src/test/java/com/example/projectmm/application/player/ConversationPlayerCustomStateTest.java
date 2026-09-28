package com.example.projectmm.application.player;

import com.example.projectmm.application.state.CustomStateService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.state.CustomStateDefinition;
import com.example.projectmm.domain.state.CustomStateValueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false
)
@Import({
        ConversationPlayerService.class,
        CustomStateService.class
})
class ConversationPlayerCustomStateTest {

    @Autowired
    ConversationPlayerService playerService;

    @Autowired
    CustomStateService customStateService;

    @Autowired
    CustomStateValueRepository values;

    @Autowired
    EntityManager em;

    @Test
    void switchingProfilesInitializesActiveStatesAndRestoresPreviousValues() {
        var character = new Character("NPC");
        var profileA = new PlayerProfile(1, "A", "Profile A", null);
        var profileB = new PlayerProfile(2, "B", "Profile B", null);

        em.persist(character);
        em.persist(profileA);
        em.persist(profileB);

        var stateA = new CustomStateDefinition(
                profileA,
                "집중력",
                0,
                100,
                50,
                "%",
                "집중 정도"
        );

        var stateB = new CustomStateDefinition(
                profileB,
                "긴장도",
                0,
                100,
                20,
                "%",
                "긴장 정도"
        );

        em.persist(stateA);
        em.persist(stateB);

        var room = new Conversation(
                character,
                LocalDateTime.of(2030, 1, 1, 12, 0)
        );
        em.persist(room);
        em.flush();

        playerService.select(character.getId(), room.getId(), profileA.getId());

        assertThat(customStateService.current(room.getId()))
                .hasSize(1)
                .first()
                .satisfies(state -> {
                    assertThat(state.definitionId()).isEqualTo(stateA.getId());
                    assertThat(state.currentValue()).isEqualTo(50);
                });

        customStateService.updateByUser(room.getId(), stateA.getId(), 80);

        playerService.select(character.getId(), room.getId(), profileB.getId());

        assertThat(customStateService.current(room.getId()))
                .hasSize(1)
                .first()
                .satisfies(state -> {
                    assertThat(state.definitionId()).isEqualTo(stateB.getId());
                    assertThat(state.currentValue()).isEqualTo(20);
                });

        playerService.select(character.getId(), room.getId(), profileA.getId());

        assertThat(customStateService.current(room.getId()))
                .hasSize(1)
                .first()
                .satisfies(state -> {
                    assertThat(state.definitionId()).isEqualTo(stateA.getId());
                    assertThat(state.currentValue()).isEqualTo(80);
                });

        assertThat(values.findByConversationIdAndDefinitionId(
                room.getId(),
                stateB.getId()
        )).isPresent();

        assertThat(values.count()).isEqualTo(2);
    }

    @Test
    void userCanCorrectPlayerCoreStateAndValuesAreClamped() {
        var character = new Character("NPC");
        em.persist(character);

        var room = new Conversation(
                character,
                LocalDateTime.of(2030, 1, 1, 12, 0)
        );

        em.persist(room);
        em.flush();

        var updated = playerService.updateCoreState(
                character.getId(),
                room.getId(),
                82,
                64
        );

        assertThat(updated.energy()).isEqualTo(82);
        assertThat(updated.mood()).isEqualTo(64);

        var current = playerService.currentCoreState(
                character.getId(),
                room.getId()
        );

        assertThat(current.energy()).isEqualTo(82);
        assertThat(current.mood()).isEqualTo(64);

        var clamped = playerService.updateCoreState(
                character.getId(),
                room.getId(),
                150,
                -20
        );

        assertThat(clamped.energy()).isEqualTo(100);
        assertThat(clamped.mood()).isZero();

        em.flush();
        em.clear();

        var saved = playerService.currentCoreState(
                character.getId(),
                room.getId()
        );

        assertThat(saved.energy()).isEqualTo(100);
        assertThat(saved.mood()).isZero();
    }

    @Test
    void playerCoreCorrectionIsRoomScopedAndRejectsWrongCharacter() {
        var character = new Character("NPC");
        var otherCharacter = new Character("OTHER");

        em.persist(character);
        em.persist(otherCharacter);

        var firstRoom = new Conversation(
                character,
                LocalDateTime.of(2030, 1, 1, 12, 0)
        );

        var secondRoom = new Conversation(
                character,
                LocalDateTime.of(2030, 1, 2, 12, 0)
        );

        em.persist(firstRoom);
        em.persist(secondRoom);
        em.flush();

        playerService.updateCoreState(
                character.getId(),
                firstRoom.getId(),
                25,
                40
        );

        assertThat(
                playerService.currentCoreState(
                        character.getId(),
                        firstRoom.getId()
                ).energy()
        ).isEqualTo(25);

        assertThat(
                playerService.currentCoreState(
                        character.getId(),
                        secondRoom.getId()
                ).energy()
        ).isEqualTo(70);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                playerService.updateCoreState(
                        otherCharacter.getId(),
                        firstRoom.getId(),
                        99,
                        99
                )
        ).isInstanceOf(
                org.springframework.web.server.ResponseStatusException.class
        );

        assertThat(
                playerService.currentCoreState(
                        character.getId(),
                        firstRoom.getId()
                ).energy()
        ).isEqualTo(25);
    }
}
