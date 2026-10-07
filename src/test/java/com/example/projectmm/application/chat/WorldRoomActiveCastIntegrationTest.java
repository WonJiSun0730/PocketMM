package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.application.time.TimeInterpretation;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldRepository;
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
        WorldRoomSessionService.class,
        WorldRoomContextBuilder.class,
        WorldRoomDialogueService.class
})
class WorldRoomActiveCastIntegrationTest {

    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRoomDialogueService dialogueService;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired CharacterRepository characters;
    @Autowired WorldConversationCastRepository roomCasts;

    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService memoryRecallService;
    @MockitoBean TimeInterpreter timeInterpreter;

    @Test
    void castCanEnterLeaveReenterAndOverlapWithinOneSession() {
        when(timeInterpreter.interpret(any(), any(LocalDateTime.class)))
                .thenReturn(TimeInterpretation.noMatch());

        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        var member = members.save(com.example.projectmm.domain.member.Member.createLocal(
                "cast-user", "password", "Eleanor Watson",
                "cast-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Victorian London", "Victorian mystery."));
        var room = rooms.save(new WorldConversation(
                world, member, "The Baker Street Case",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0)
        ));
        var intro = intros.save(new WorldRoomIntro(
                room, "Fog over Baker Street", "A dense fog hangs over Baker Street.", 0
        ));

        Character sherlock = characters.save(new Character("Sherlock Holmes", member));
        sherlock.assignWorld(world);
        Character victor = characters.save(new Character("Victor Hale", member));
        victor.assignWorld(world);

        roomCasts.save(new WorldConversationCast(
                room, sherlock, LocalDateTime.of(1889, 11, 9, 19, 0)
        ));
        roomCasts.save(new WorldConversationCast(
                room, victor, LocalDateTime.of(1889, 11, 9, 19, 0).plusSeconds(1)
        ));

        var session = sessionService.start(
                room.getId(), member.getLoginId(), null, intro.getId()
        );

        assertThat(sessionService.activeCast(session.getId())).isEmpty();

        when(gateway.generateWorldRoomTurn(any(), eq("I knock.")))
                .thenReturn(new WorldRoomTurn(
                        "Sherlock Holmes: \"Come in.\"",
                        new WorldRoomCastChange(List.of(sherlock.getId()), List.of()),
                        List.of()
                ));
        dialogueService.chat(session.getId(), "I knock.");

        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getName())
                .containsExactly("Sherlock Holmes");

        when(gateway.generateWorldRoomTurn(any(), eq("A second visitor arrives.")))
                .thenReturn(new WorldRoomTurn(
                        "Victor Hale: \"I hope I am not interrupting.\"",
                        new WorldRoomCastChange(List.of(victor.getId()), List.of()),
                        List.of()
                ));
        dialogueService.chat(session.getId(), "A second visitor arrives.");

        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getName())
                .containsExactlyInAnyOrder("Sherlock Holmes", "Victor Hale");

        when(gateway.generateWorldRoomTurn(any(), eq("Sherlock leaves to inspect the alley.")))
                .thenReturn(new WorldRoomTurn(
                        "Victor Hale watches the door close behind Holmes.",
                        new WorldRoomCastChange(List.of(), List.of(sherlock.getId())),
                        List.of()
                ));
        dialogueService.chat(session.getId(), "Sherlock leaves to inspect the alley.");

        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getName())
                .containsExactly("Victor Hale");

        when(gateway.generateWorldRoomTurn(any(), eq("Footsteps return on the stairs.")))
                .thenReturn(new WorldRoomTurn(
                        "Sherlock Holmes: \"The alley told me enough.\"",
                        new WorldRoomCastChange(List.of(sherlock.getId()), List.of()),
                        List.of()
                ));
        dialogueService.chat(session.getId(), "Footsteps return on the stairs.");

        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getName())
                .containsExactlyInAnyOrder("Sherlock Holmes", "Victor Hale");

        var applied = sessionService.applyCastChange(
                session.getId(),
                new WorldRoomCastChange(
                        List.of(999999L, sherlock.getId(), sherlock.getId()),
                        List.of(888888L, victor.getId(), victor.getId())
                )
        );

        assertThat(applied.enterCharacterIds()).isEmpty();
        assertThat(applied.leaveCharacterIds()).containsExactly(victor.getId());
        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getName())
                .containsExactly("Sherlock Holmes");
    }
}
