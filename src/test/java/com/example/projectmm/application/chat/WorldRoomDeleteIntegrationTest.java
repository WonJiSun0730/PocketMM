package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomSystemChange;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldRepository;
import com.example.projectmm.domain.world.WorldSystem;
import com.example.projectmm.domain.world.WorldSystemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({
        WorldRoomService.class,
        WorldRoomSessionService.class,
        WorldRoomContextBuilder.class,
        WorldRoomDialogueService.class
})
class WorldRoomDeleteIntegrationTest {

    @Autowired WorldRoomService roomService;
    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRoomDialogueService dialogueService;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldSystemRepository systems;
    @Autowired WorldRoomSessionRepository sessions;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomMessageVariantRepository variants;
    @Autowired WorldRoomVariantCastSnapshotRepository castSnapshots;
    @Autowired WorldRoomVariantSystemSnapshotRepository systemSnapshots;
    @Autowired WorldRoomSessionCastRepository sessionCasts;
    @Autowired WorldRoomSessionSystemStateRepository sessionSystemStates;

    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService memoryRecallService;

    @Test
    void deletingRoomRemovesPlayDataButKeepsWorld() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "room-delete-owner", "password", "Eleanor Watson",
                "room-delete@example.test", encoder
        ));
        World world = worlds.save(new World(
                member, "Victorian London", "Disposable integration fixture."
        ));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "The Baker Street Case",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.now()
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(
                room, "Fog over Baker Street", "The case begins.", 0
        ));
        WorldSystem tension = systems.save(new WorldSystem(
                world, "Case Tension", "Changes with danger.",
                0, 100, 20, 0
        ));

        var session = sessionService.start(
                room.getId(), member.getLoginId(), null, intro.getId()
        );

        when(gateway.generateWorldRoomTurn(any(), eq("I knock.")))
                .thenReturn(new WorldRoomTurn(
                        "A voice answers from within.",
                        WorldRoomCastChange.none(),
                        List.of(new WorldRoomSystemChange(tension.getId(), 5))
                ));

        dialogueService.chat(session.getId(), "I knock.");

        assertThat(messages.findTop50BySessionIdOrderByIdDesc(session.getId())).hasSize(2);
        var characterMessage = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.CHARACTER)
                .findFirst()
                .orElseThrow();
        var variant = variants.findByMessageIdOrderByVariantNoAsc(characterMessage.getId())
                .getFirst();
        assertThat(castSnapshots.findByVariantIdOrderByCharacterIdAsc(variant.getId())).isEmpty();
        assertThat(systemSnapshots.findByVariantIdOrderBySystemIdAsc(variant.getId())).isNotEmpty();
        assertThat(sessionSystemStates.findBySessionIdOrderBySystemSortOrderAscIdAsc(session.getId()))
                .isNotEmpty();

        roomService.deleteRoom(room.getId(), member.getLoginId());

        assertThat(rooms.findById(room.getId())).isEmpty();
        assertThat(sessions.findById(session.getId())).isEmpty();
        assertThat(messages.findTop50BySessionIdOrderByIdDesc(session.getId())).isEmpty();
        assertThat(variants.findByMessageIdOrderByVariantNoAsc(characterMessage.getId())).isEmpty();
        assertThat(systemSnapshots.findByVariantIdOrderBySystemIdAsc(variant.getId())).isEmpty();
        assertThat(sessionCasts.findBySessionIdAndActiveTrueOrderByEnteredAtAscIdAsc(session.getId())).isEmpty();
        assertThat(sessionSystemStates.findBySessionIdOrderBySystemSortOrderAscIdAsc(session.getId())).isEmpty();
        assertThat(intros.findById(intro.getId())).isEmpty();

        assertThat(worlds.findById(world.getId())).isPresent();
        assertThat(systems.findById(tension.getId())).isPresent();
    }
}
