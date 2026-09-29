package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.memory.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
class MemoryAccessCandidateQueryTest {
    @Autowired EntityManager em;
    @Autowired MemoryAccessStateRepository access;

    @Test void batchQueryReturnsOnlyRestrictedRequestedIdsAndPreservesLegacyExplicit() {
        var now = LocalDateTime.of(2026, 9, 29, 12, 0);
        var character = new Character("NPC");
        em.persist(character);
        var ids = new ArrayList<Long>();
        var expected = new ArrayList<Long>();
        for (var level : MemoryAccessLevel.values()) {
            var memory = new Memory(character, null, "memory", 5, 50, now);
            em.persist(memory);
            em.persist(new MemoryAccessState(memory, level, now));
            ids.add(memory.getId());
            if (level != MemoryAccessLevel.EXPLICIT) expected.add(memory.getId());
        }
        var legacy = new Memory(character, null, "legacy", 5, 50, now);
        em.persist(legacy);
        ids.add(legacy.getId());
        em.flush();
        em.clear();
        assertThat(access.findMemoryIdsWithDifferentAccess(ids, MemoryAccessLevel.EXPLICIT))
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(access.findMemoryIdsWithDifferentAccess(List.of(legacy.getId()), MemoryAccessLevel.EXPLICIT)).isEmpty();
    }
}
