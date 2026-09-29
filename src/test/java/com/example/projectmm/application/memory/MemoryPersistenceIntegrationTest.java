package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.event.EventRepository;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.scene.SceneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 개발용 MySQL이나 외부 서비스 없이 실제 JPA 저장 경로를 검증한다.
@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({
        MemoryCreationService.class,
        MemoryDetailService.class,
        MemoryPolicy.class
})
@Transactional
class MemoryPersistenceIntegrationTest {

    @Autowired
    private jakarta.persistence.EntityManager em;

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

    @Test
    void 중요한_이벤트가_실제_기억으로_저장된다() {

        Character millie =
                characterRepository.save(
                        new Character("MemoryTestMillie")
                );

        LocalDateTime occurredAt =
                LocalDateTime.of(
                        2026, 9, 20,
                        20, 0
                );

        Scene scene =
                sceneRepository.save(
                        new Scene(
                                occurredAt,
                                "기억 테스트",
                                "DAILY"
                        )
                );

        Event event =
                eventRepository.save(
                        new Event(
                                scene,
                                occurredAt,
                                "INTERACTION",
                                "오랫동안 기억할 중요한 대화를 나누었다.",
                                7
                        )
                );

        eventParticipantRepository.save(
                new EventParticipant(
                        event,
                        millie,
                        "ACTOR"
                )
        );

        List<Memory> created =
                memoryCreationService.createFrom(
                        event
                );

        assertThat(created)
                .hasSize(1);

        // 1차 캐시의 객체가 아닌 실제 저장/재조회 결과를 검증한다.
        em.flush();
        em.clear();
        List<Memory> memories =
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                millie.getId()
                        );

        assertThat(memories)
                .hasSize(1);

        Memory memory =
                memories.getFirst();

        assertThat(memory.getCharacter().getId())
                .isEqualTo(millie.getId());

        assertThat(memory.getSourceEvent().getId())
                .isEqualTo(event.getId());

        assertThat(memory.getSummary())
                .isEqualTo(
                        "오랫동안 기억할 중요한 대화를 나누었다."
                );

        assertThat(memory.getImportance())
                .isEqualTo(7);

        assertThat(memory.getStrength())
                .isEqualTo(70);

        assertThat(memory.getRememberedAt())
                .isEqualTo(occurredAt);
    }
}
