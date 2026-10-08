package com.example.projectmm.application.world;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.api.world.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.member.*;
import com.example.projectmm.domain.world.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest @Import({WorldSystemService.class, WorldRoomSessionService.class, WorldRoomContextBuilder.class})
class WorldSystemPersistenceIntegrationTest {
    @Autowired WorldSystemService service;
    @Autowired WorldRoomSessionService sessions;
    @Autowired WorldRoomContextBuilder contexts;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldSystemRepository systems;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldRoomSessionSystemStateRepository values;
    @Autowired EntityManager entityManager;
    @MockitoBean MemoryRecallService memories;

    World world() {
        var encoder = mock(PasswordEncoder.class); when(encoder.encode("pw")).thenReturn("encoded");
        var owner = members.save(Member.createLocal("tester", "pw", "Tester", "tester@example.test", encoder));
        return worlds.save(new World(owner, "World", "Description"));
    }
    WorldSystemItem definition(int initial, int max, String label) {
        return new WorldSystemItem("피에타", label, 0, max, initial, List.of(new WorldSystemStateItem(0, max, label)));
    }
    @Test void savesInitialValueThenKeepsOldPocketDefinitionsAcrossEditsAndRemoval() {
        var world = world(); Long worldId = world.getId();
        service.replace(worldId, new WorldSystemsRequest("guide", List.of(definition(37, 100, "old"))), "tester");
        var at = LocalDateTime.of(2026, 10, 8, 15, 0);
        var room = rooms.save(new WorldConversation(world, world.getOwner(), "Room", WorldConversation.Visibility.PRIVATE, at));
        var intro = intros.save(new WorldRoomIntro(room, "Start", "Free start", 0));
        var first = sessions.start(room.getId(), "tester", null, intro.getId());
        var oldValue = values.findBySessionIdOrderBySystemSortOrderAscIdAsc(first.getId()).getFirst();
        assertThat(oldValue.getCurrentValue()).isEqualTo(37); oldValue.setCurrentValue(42);
        Long oldSystemId = oldValue.getSystem().getId();
        service.replace(worldId, new WorldSystemsRequest("guide", List.of(definition(70, 200, "new"))), "tester");
        var second = sessions.start(room.getId(), "tester", null, intro.getId());
        Long firstId = first.getId(), secondId = second.getId(), roomId = room.getId(), introId = intro.getId();
        entityManager.flush(); entityManager.clear();
        var stored = service.get(worldId, "tester").systems().getFirst();
        assertThat(stored.initialValue()).isEqualTo(70);
        assertThat(systems.findById(oldSystemId).orElseThrow().isArchived()).isTrue();
        var oldContext = contexts.buildSession(firstId);
        assertThat(oldContext.sessionSystems().getFirst().currentValue()).isEqualTo(42);
        assertThat(oldContext.world().systems().getFirst().maxValue()).isEqualTo(100);
        assertThat(oldContext.world().systems().getFirst().changeCondition()).isEqualTo("old");
        assertThat(contexts.buildSession(secondId).sessionSystems().getFirst().currentValue()).isEqualTo(70);
        assertThat(contexts.buildSession(secondId).world().systems().getFirst().maxValue()).isEqualTo(200);
        service.replace(worldId, new WorldSystemsRequest("guide", List.of()), "tester");
        var third = sessions.start(roomId, "tester", null, introId);
        entityManager.flush(); entityManager.clear();
        assertThat(service.get(worldId, "tester").systems()).isEmpty();
        assertThat(contexts.buildSession(third.getId()).sessionSystems()).isEmpty();
        assertThat(contexts.buildSession(firstId).sessionSystems().getFirst().currentValue()).isEqualTo(42);
        assertThat(contexts.buildSession(secondId).sessionSystems().getFirst().currentValue()).isEqualTo(70);
    }
    @Test void malformedInitialValueDoesNotArchiveExistingDefinitions() {
        var world = world();
        service.replace(world.getId(), new WorldSystemsRequest("original", List.of(definition(37, 100, "old"))), "tester");
        assertThatThrownBy(() -> service.replace(world.getId(), new WorldSystemsRequest("changed", List.of(definition(101, 100, "bad"))), "tester"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.get(world.getId(), "tester").systems().getFirst().initialValue()).isEqualTo(37);
        assertThat(world.getPlayGuide()).isEqualTo("original");
    }
    @Test void missingInitialValueParsesAsMissingAndGetsFieldValidationInsteadOfPrimitiveParseTrace() {
        var request = new ObjectMapper().readValue("{\"systems\":[{\"name\":\"Test\",\"changeCondition\":\"\",\"minValue\":0,\"maxValue\":100,\"states\":[]}]}", WorldSystemsRequest.class);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).anySatisfy(v -> {
                assertThat(v.getPropertyPath().toString()).contains("initialValue");
                assertThat(v.getMessage()).isEqualTo("시스템 초기값을 입력해주세요.");
            });
        }
    }
}
