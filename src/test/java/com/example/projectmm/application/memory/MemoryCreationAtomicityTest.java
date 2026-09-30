package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.event.EventRepository;
import com.example.projectmm.domain.memory.MemoryDetailSalience;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.scene.SceneRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false
)
@Import({
        MemoryCreationService.class,
        MemoryDetailService.class,
        MemoryPolicy.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemoryCreationAtomicityTest {

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventParticipantRepository eventParticipantRepository;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private MemoryCreationService memoryCreationService;

    @MockitoBean
    private ProtectedMemoryDetailRepository protectedMemoryDetailRepository;

    @AfterEach
    void cleanUp() {
        memoryRepository.deleteAll();
        eventParticipantRepository.deleteAll();
        eventRepository.deleteAll();
        sceneRepository.deleteAll();
        characterRepository.deleteAll();
    }

    @Test
    void detailPersistenceFailureRollsBackMemoryCreation() {

        Character character =
                characterRepository.save(
                        new Character("Memory Atomicity QA")
                );

        LocalDateTime occurredAt =
                LocalDateTime.of(
                        2026, 9, 30,
                        12, 0
                );

        Scene scene =
                sceneRepository.save(
                        new Scene(
                                occurredAt,
                                "Memory atomicity test",
                                "DAILY"
                        )
                );

        Event event =
                eventRepository.save(
                        new Event(
                                scene,
                                occurredAt,
                                "INTERACTION",
                                "Atomic memory test event",
                                7
                        )
                );

        eventParticipantRepository.save(
                new EventParticipant(
                        event,
                        character,
                        "ACTOR"
                )
        );

        doThrow(new IllegalStateException("detail persistence failed"))
                .when(protectedMemoryDetailRepository)
                .save(any());

        assertThatThrownBy(() ->
                memoryCreationService.createFrom(
                        event,
                        List.of(
                                new MemoryDetailCandidate(
                                        "important detail",
                                        MemoryDetailSalience.HIGH,
                                        true
                                )
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("detail persistence failed");

        assertThat(
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                character.getId()
                        )
        ).isEmpty();
    }
}