package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        },
        showSql = false
)
@Import(MemoryLifecycleService.class)
class MemoryLifecycleServiceTest {

    @Autowired
    EntityManager em;

    @Autowired
    MemoryRepository memoryRepository;

    @Autowired
    MemoryLifecycleService memoryLifecycleService;

    @Test
    void 실제_회상된_기억의_마지막_접근_시각을_저장한다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 8, 1,
                        12, 0
                );

        LocalDateTime accessedAt =
                LocalDateTime.of(
                        2026, 9, 29,
                        0, 30
                );

        Character character =
                new Character("Lifecycle QA");

        em.persist(character);

        Memory memory =
                new Memory(
                        character,
                        null,
                        "오래된 기억",
                        6,
                        60,
                        rememberedAt
                );

        em.persist(memory);
        em.flush();

        Long memoryId = memory.getId();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        memoryLifecycleService.markAccessed(
                List.of(memoryId),
                accessedAt
        );

        Memory reloaded =
                memoryRepository
                        .findById(memoryId)
                        .orElseThrow();

        assertThat(reloaded.getLastAccessedAt())
                .isEqualTo(accessedAt);
    }

    @Test
    void 오래_접근하지_않은_기억을_다시_회상하면_재활성화한다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 8, 1,
                        12, 0
                );

        LocalDateTime reactivatedAt =
                rememberedAt.plusDays(40);

        Character character =
                new Character("Reactivation QA");

        em.persist(character);

        Memory memory =
                new Memory(
                        character,
                        null,
                        "오랫동안 잠들어 있던 기억",
                        6,
                        60,
                        rememberedAt
                );

        em.persist(memory);
        em.flush();

        Long memoryId = memory.getId();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        memoryLifecycleService.markAccessed(
                List.of(memoryId),
                reactivatedAt
        );

        Memory reloaded =
                memoryRepository
                        .findById(memoryId)
                        .orElseThrow();

        assertThat(reloaded.getLastAccessedAt())
                .isEqualTo(reactivatedAt);

        assertThat(reloaded.getLastReactivatedAt())
                .isEqualTo(reactivatedAt);
    }

    @Test
    void 최근_접근한_기억은_일반_접근만_갱신하고_재활성화하지_않는다() {

        LocalDateTime rememberedAt =
                LocalDateTime.of(
                        2026, 9, 20,
                        12, 0
                );

        LocalDateTime accessedAt =
                rememberedAt.plusDays(5);

        Character character =
                new Character("Recent Access QA");

        em.persist(character);

        Memory memory =
                new Memory(
                        character,
                        null,
                        "최근 기억",
                        6,
                        60,
                        rememberedAt
                );

        em.persist(memory);
        em.flush();

        Long memoryId = memory.getId();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        memoryLifecycleService.markAccessed(
                List.of(memoryId),
                accessedAt
        );

        Memory reloaded =
                memoryRepository
                        .findById(memoryId)
                        .orElseThrow();

        assertThat(reloaded.getLastAccessedAt())
                .isEqualTo(accessedAt);

        assertThat(reloaded.getLastReactivatedAt())
                .isNull();
    }
}