package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomSystemChange;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.time.TimeInterpretation;
import com.example.projectmm.domain.time.TimeChange;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldRepository;
import com.example.projectmm.domain.world.WorldSystem;
import com.example.projectmm.domain.world.WorldSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({
        WorldRoomSessionService.class,
        WorldRoomContextBuilder.class,
        WorldRoomDialogueService.class
})
class WorldRoomDialogueWorldlineIntegrationTest {

    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRoomDialogueService dialogueService;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldSystemRepository systems;
    @Autowired CharacterRepository characters;
    @Autowired WorldConversationCastRepository roomCasts;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomMessageVariantRepository variants;
    @Autowired WorldRoomSessionRepository sessions;

    @MockitoBean DialogueGateway gateway;
    @MockitoBean TimeInterpreter timeInterpreter;
    @MockitoBean MemoryRecallService memoryRecallService;

    @BeforeEach
    void defaultTimeInterpretation() {
        when(timeInterpreter.interpret(anyString(), any(LocalDateTime.class)))
                .thenReturn(TimeInterpretation.noMatch());
    }

    @Test
    void explicitUserTimeChangeUpdatesPocketClockBeforeDialogueGeneration() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "time-user", "password", "Time User",
                "time-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Time World", "Time test."));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Time Room",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 2, 1, 47)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(
                room, "Start", "Start scene.", 0
        ));
        var session = sessionService.start(
                room.getId(), member.getLoginId(), null, intro.getId()
        );

        var before = session.getSimulatedAt();
        var after = before.plusDays(3);
        when(timeInterpreter.interpret(anyString(), eq(before)))
                .thenReturn(TimeInterpretation.matched(
                        TimeInterpretation.Interpreter.RULE,
                        null,
                        TimeChange.changed(before, after, "3일 후")
                ));
        when(gateway.generateWorldRoomTurn(
                argThat(context -> context != null && after.equals(context.simulatedAt())),
                anyString()
        )).thenReturn(new WorldRoomTurn(
                "*3일 후의 장면이 시작된다.*",
                WorldRoomCastChange.none(),
                List.of()
        ));

        dialogueService.chat(
                session.getId(),
                "[OOC] 3일 후의 다른 시점부터 이야기를 재시작하라."
        );

        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(after);
        verify(gateway).generateWorldRoomTurn(
                argThat(context -> after.equals(context.simulatedAt())),
                anyString()
        );
    }

    @Test
    void rerollAndVariantSelectionRestoreTextCastAndSystemAsOneWorldline() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "eleanor2", "password", "Eleanor Watson",
                "eleanor2@example.test", encoder
        ));
        World world = worlds.save(new World(
                member, "Victorian London", "Victorian mystery."
        ));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "The Baker Street Case",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(
                room, "Fog over Baker Street",
                "A dense fog hangs over Baker Street.", 0
        ));
        WorldSystem tension = systems.save(new WorldSystem(
                world, "Case Tension", "Increase when danger escalates.",
                0, 100, 20, 0
        ));

        Character sherlock = characters.save(new Character("Sherlock Holmes", member));
        sherlock.assignWorld(world);
        roomCasts.save(new WorldConversationCast(
                room, sherlock, LocalDateTime.of(1889, 11, 9, 19, 0)
        ));

        var session = sessionService.start(
                room.getId(), member.getLoginId(), null, intro.getId()
        );

        when(gateway.generateWorldRoomTurn(any(), eq("I knock on the door.")))
                .thenReturn(new WorldRoomTurn(
                        "Sherlock Holmes: \"Come in.\"",
                        new WorldRoomCastChange(List.of(sherlock.getId()), List.of()),
                        List.of(new WorldRoomSystemChange(tension.getId(), 10))
                ));

        var first = dialogueService.chat(session.getId(), "I knock on the door.");

        assertThat(first.reply()).isEqualTo("Sherlock Holmes: \"Come in.\"");
        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getId())
                .containsExactly(sherlock.getId());
        assertThat(sessionService.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getCurrentValue()).isEqualTo(30));

        when(gateway.generateWorldRoomTurn(
                any(),
                argThat(prompt -> prompt != null
                        && prompt.startsWith("I knock on the door.")
                        && prompt.contains("[Previous response variant]")
                        && prompt.contains("Sherlock Holmes: \"Come in.\""))
        )).thenReturn(new WorldRoomTurn(
                        "Sherlock Holmes: \"You are late.\"",
                        WorldRoomCastChange.none(),
                        List.of(new WorldRoomSystemChange(tension.getId(), -5))
                ));

        var characterMessage = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.CHARACTER)
                .findFirst()
                .orElseThrow();

        var second = dialogueService.reroll(session.getId(), characterMessage.getId());

        assertThat(second.reply()).isEqualTo("Sherlock Holmes: \"You are late.\"");
        assertThat(sessionService.activeCast(session.getId())).isEmpty();
        assertThat(sessionService.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getCurrentValue()).isEqualTo(15));

        dialogueService.selectVariant(session.getId(), characterMessage.getId(), 1);

        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getId())
                .containsExactly(sherlock.getId());
        assertThat(sessionService.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getCurrentValue()).isEqualTo(30));

        dialogueService.selectVariant(session.getId(), characterMessage.getId(), 2);

        assertThat(sessionService.activeCast(session.getId())).isEmpty();
        assertThat(sessionService.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getCurrentValue()).isEqualTo(15));

        when(gateway.generateWorldRoomTurn(any(), eq("I step into the room.")))
                .thenReturn(new WorldRoomTurn(
                        "The fire crackles in the grate.",
                        WorldRoomCastChange.none(),
                        List.of()
                ));

        dialogueService.chat(session.getId(), "I step into the room.");

        dialogueService.editMessage(
                session.getId(),
                characterMessage.getId(),
                "Sherlock Holmes: \"You are very late.\""
        );

        assertThat(messages.findById(characterMessage.getId()).orElseThrow().getContent())
                .isEqualTo("Sherlock Holmes: \"You are very late.\"");
        assertThat(variants.findByMessageIdOrderByVariantNoAsc(characterMessage.getId()))
                .filteredOn(variant -> variant.getVariantNo() == 2)
                .singleElement()
                .satisfies(variant -> assertThat(variant.getContent())
                        .isEqualTo("Sherlock Holmes: \"You are very late.\""));

        assertThatThrownBy(() ->
                dialogueService.selectVariant(session.getId(), characterMessage.getId(), 1)
        ).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("finalized room message");

        assertThatThrownBy(() ->
                dialogueService.reroll(session.getId(), characterMessage.getId())
        ).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("finalized room message");

        var laterUserMessage = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.USER)
                .findFirst()
                .orElseThrow();

        dialogueService.deleteMessageAndFollowing(session.getId(), laterUserMessage.getId());

        dialogueService.selectVariant(session.getId(), characterMessage.getId(), 1);

        assertThat(sessionService.activeCast(session.getId()))
                .extracting(state -> state.getCharacter().getId())
                .containsExactly(sherlock.getId());
        assertThat(sessionService.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getCurrentValue()).isEqualTo(30));
    }
}
