package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.scene.Scene;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"}, showSql = false)
@Import({MemoryCreationService.class, MemoryPolicy.class, MemoryDetailService.class})
class MemoryParticipantOwnershipTest {
    @Autowired EntityManager em;
    @Autowired MemoryCreationService service;
    @Autowired MemoryRepository memories;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void eachParticipantOwnsOneMemoryWhileSourceScopeIsPreserved(boolean conversationEvent) {
        var at = LocalDateTime.of(2030, 1, 1, 12, 0);
        var a = new Character("A");
        var b = new Character("B");
        em.persist(a); em.persist(b);
        var room = Conversation.fresh(a, at);
        var otherRoom = Conversation.fresh(b, at);
        em.persist(room); em.persist(otherRoom);
        var scene = new Scene(at, "Shared event", "DAILY");
        em.persist(scene);
        var event = new Event(scene, at, "INTERACTION", "A and B made a promise.", 7, 8);
        em.persist(event);
        em.persist(new EventParticipant(event, a, "ACTOR"));
        em.persist(new EventParticipant(event, b, "PARTICIPANT"));
        em.flush();

        var created = conversationEvent ? service.createFrom(event, List.of(), room) : service.createFrom(event);
        assertThat(created).hasSize(2);
        var ids = created.stream().map(Memory::getId).toList();
        em.flush(); em.clear();

        var stored = memories.findAllById(ids);
        assertThat(memories.count()).isEqualTo(2);
        assertThat(stored).extracting(m -> m.getCharacter().getId())
                .containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(stored).allSatisfy(memory -> {
            assertThat(memory.getSourceEvent().getId()).isEqualTo(event.getId());
            if (conversationEvent) assertThat(memory.getConversation().getId()).isEqualTo(room.getId());
            else assertThat(memory.getConversation()).isNull();
            assertThat(memory.getImportance()).isEqualTo(7);
            assertThat(memory.getNarrativeAnchor()).isEqualTo(8);
        });
        assertThat(memories.findForConversation(a.getId(), room.getId())).hasSize(1);
        assertThat(memories.findForConversation(b.getId(), room.getId())).hasSize(1);
        assertThat(memories.findForConversation(b.getId(), otherRoom.getId()))
                .hasSize(conversationEvent ? 0 : 1);
    }
}
