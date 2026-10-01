package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomSystemChange;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({
        WorldRoomSessionService.class,
        WorldRoomContextBuilder.class,
        WorldRoomDialogueService.class
})
class WorldRoomRegenerateIntegrationTest {

    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRoomDialogueService dialogueService;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldSystemRepository systems;
    @Autowired WorldRoomMessageRepository messages;

    @MockitoBean DialogueGateway gateway;

    @Test
    void regenerateRemovesOldWorldlineFromContextAndAppliesReplacementState() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        var member = members.save(com.example.projectmm.domain.member.Member.createLocal(
                "regen-user", "password", "Eleanor Watson",
                "regen-user@example.test", encoder
        ));
        var world = worlds.save(new World(member, "Victorian London", "Victorian mystery."));
        var room = rooms.save(new WorldConversation(
                world, member, "The Baker Street Case",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0)
        ));
        var intro = intros.save(new WorldRoomIntro(
                room, "Fog over Baker Street", "A dense fog hangs over Baker Street.", 0
        ));
        WorldSystem tension = systems.save(new WorldSystem(
                world, "Case Tension", "Increase when danger escalates.",
                0, 100, 20, 0
        ));

        var session = sessionService.start(
                room.getId(), member.getLoginId(), null, intro.getId()
        );

        when(gateway.generateWorldRoomTurn(any(), eq("I open the letter.")))
                .thenReturn(
                        new WorldRoomTurn(
                                "The letter contains a threat.",
                                WorldRoomCastChange.none(),
                                List.of(new WorldRoomSystemChange(tension.getId(), 30))
                        ),
                        new WorldRoomTurn(
                                "The letter contains a quiet invitation.",
                                WorldRoomCastChange.none(),
                                List.of(new WorldRoomSystemChange(tension.getId(), 5))
                        )
                );

        dialogueService.chat(session.getId(), "I open the letter.");

        var oldCharacter = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.CHARACTER)
                .findFirst()
                .orElseThrow();

        assertThat(sessionService.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getCurrentValue()).isEqualTo(50));

        var regenerated = dialogueService.regenerate(session.getId(), oldCharacter.getId());

        assertThat(regenerated.reply()).isEqualTo("The letter contains a quiet invitation.");
        assertThat(sessionService.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getCurrentValue()).isEqualTo(25));

        assertThat(messages.findById(oldCharacter.getId())).isEmpty();

        var currentMessages = dialogueService.history(session.getId(), null);
        assertThat(currentMessages)
                .extracting(WorldRoomMessage::getContent)
                .containsExactly(
                        "I open the letter.",
                        "The letter contains a quiet invitation."
                );

        verify(gateway, times(2)).generateWorldRoomTurn(
                argThat(context -> context != null
                        && context.recentMessages().stream()
                        .noneMatch(message -> "The letter contains a threat.".equals(message.content()))),
                eq("I open the letter.")
        );
    }
}
