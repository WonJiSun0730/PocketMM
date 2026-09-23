package com.example.projectmm.presentation.state;

import com.example.projectmm.application.state.CustomStateService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({CustomStateService.class, CustomStateController.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomStateControllerTest {
    @Autowired CustomStateController controller;
    @Autowired CustomStateService service;
    @Autowired CustomStateValueRepository values;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    private MockMvc mvc;
    private Long roomA;
    private Long roomB;
    private Long emptyRoom;
    private Long noProfileRoom;
    private Long pieta;
    private Long mana;
    private Long foreignDefinition;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var profile = new PlayerProfile(1, "Player", "Description", null);
            var other = new PlayerProfile(2, "Other", "Description", null);
            var character = new Character("NPC");
            em.persist(profile);
            em.persist(other);
            em.persist(character);
            roomA = room(character, profile);
            roomB = room(character, profile);
            emptyRoom = room(character, profile);
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
        service.updateByUser(roomA, pieta, 67);
        service.updateByUser(roomA, mana, 50.5);
        service.updateByUser(roomB, pieta, 12);
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
    void getReturnsOnlyStoredRoomValuesWithInputMetadata() throws Exception {
        mvc.perform(get(url(roomA))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].definitionId").value(pieta))
                .andExpect(jsonPath("$[0].name").value("피에타"))
                .andExpect(jsonPath("$[0].currentValue").value(67.0))
                .andExpect(jsonPath("$[0].minValue").value(0.0))
                .andExpect(jsonPath("$[0].maxValue").value(100.0))
                .andExpect(jsonPath("$[0].unit").value("%"))
                .andExpect(jsonPath("$[0].description").value("신앙"))
                .andExpect(jsonPath("$[1].currentValue").value(50.5));
        mvc.perform(get(url(roomB))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currentValue").value(12.0));
        assertThat(values.count()).isEqualTo(3);
        assertThat(dbValue(roomA, pieta)).isEqualTo(67);
    }

    @Test
    void emptyGetDoesNotInitializeAndInitializationIsNotExposed() throws Exception {
        mvc.perform(get(url(emptyRoom))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(url(emptyRoom))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post(url(emptyRoom))).andExpect(status().isMethodNotAllowed());
        assertThat(values.findByConversationIdOrderByIdAsc(emptyRoom)).isEmpty();
        assertThat(values.count()).isEqualTo(3);
    }

    @Test
    void patchCommitsLatestValueAndLeavesOtherDefinitionsAndRoomsUntouched() throws Exception {
        mvc.perform(patch(url(roomA) + "/" + pieta).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentValue\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionId").value(pieta))
                .andExpect(jsonPath("$.name").value("피에타"))
                .andExpect(jsonPath("$.currentValue").value(80.0))
                .andExpect(jsonPath("$.minValue").value(0.0))
                .andExpect(jsonPath("$.maxValue").value(100.0))
                .andExpect(jsonPath("$.unit").value("%"))
                .andExpect(jsonPath("$.description").value("신앙"));
        mvc.perform(get(url(roomA))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentValue").value(80.0));
        assertThat(dbValue(roomA, pieta)).isEqualTo(80);
        assertThat(dbValue(roomA, mana)).isEqualTo(50.5);
        assertThat(dbValue(roomB, pieta)).isEqualTo(12);
    }

    @Test
    void serviceRejectionsKeepTheirHttpStatusAndDoNotChangeState() throws Exception {
        mvc.perform(patch(url(roomA) + "/" + foreignDefinition).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentValue\":10}")).andExpect(status().isBadRequest());
        mvc.perform(patch(url(noProfileRoom) + "/" + pieta).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentValue\":80}")).andExpect(status().isConflict());
        mvc.perform(get(url(-1L))).andExpect(status().isNotFound());
        mvc.perform(patch(url(-1L) + "/" + pieta).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentValue\":80}")).andExpect(status().isNotFound());
        mvc.perform(patch(url(roomA) + "/-1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentValue\":80}")).andExpect(status().isNotFound());
        assertThat(dbValue(roomA, pieta)).isEqualTo(67);
        assertThat(values.count()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"currentValue\":-1}", "{\"currentValue\":101}",
            "{}", "{\"currentValue\":null}", "{\"currentValue\":\"80\"}",
            "{\"currentValue\":true}", "{\"currentValue\":[]}", "{\"currentValue\":{}}",
            "{\"currentValue\":NaN}", "{\"currentValue\":Infinity}", "{\"currentValue\":-Infinity}"})
    void invalidInputReturnsBadRequestAndPreservesDatabase(String body) throws Exception {
        mvc.perform(patch(url(roomA) + "/" + pieta).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(dbValue(roomA, pieta)).isEqualTo(67);
        assertThat(dbValue(roomA, mana)).isEqualTo(50.5);
        assertThat(values.count()).isEqualTo(3);
    }

    private Long room(Character character, PlayerProfile profile) {
        var room = new Conversation(character, LocalDateTime.of(2030, 1, 1, 12, 0));
        room.selectPlayerProfile(profile);
        em.persist(room);
        return room.getId();
    }

    private String url(Long roomId) {
        return "/api/conversations/" + roomId + "/custom-states";
    }

    private double dbValue(Long roomId, Long definitionId) {
        return values.findByConversationIdAndDefinitionId(roomId, definitionId).orElseThrow().getCurrentValue();
    }
}
