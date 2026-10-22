package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.time.RuleBasedTimeInterpreter;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import com.example.projectmm.domain.member.Member;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({WorldRoomSessionService.class, WorldRoomContextBuilder.class, WorldRoomDialogueService.class})
class WorldRoomSceneClockIntegrationTest {
    @org.junit.jupiter.api.BeforeEach
    void approveContinuityForExistingScenario() {
        com.example.projectmm.application.dialogue.SceneContinuityTestSupport.approveUnchanged(gateway);
    }

    @Autowired WorldRoomSessionService service;
    @Autowired WorldRoomDialogueService dialogue;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldRoomMessageRepository messages;
    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService memories;
    @MockitoBean TimeInterpreter interpreter;

    @Test
    void statedArrivalClockSurvivesRerollEditAndEqualClockWithoutAddingSevenMinutes() {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");
        var member = members.save(Member.createLocal("arrival", "password", "Arrival", "arrival@example.test", encoder));
        var world = worlds.save(new World(member, "TestWorld", "A world"));
        var start = LocalDateTime.of(2026, 10, 8, 15, 7);
        var room = rooms.save(new WorldConversation(world, member, "Waiting room", WorldConversation.Visibility.PRIVATE, start));
        var intro = intros.save(new WorldRoomIntro(room, "Free", "Start freely", 0, start, "상담소 대기실", List.of(), List.of()));
        var session = service.start(room.getId(), member.getLoginId(), null, intro.getId());
        var rule = new RuleBasedTimeInterpreter();
        when(interpreter.interpret(anyString(), any(LocalDateTime.class)))
                .thenAnswer(call -> rule.interpret(call.getArgument(0), call.getArgument(1)));
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(
                new WorldRoomTurn("We are back in the waiting room.", WorldRoomCastChange.none(), List.of(), 420));
        String input = "아까 올라오면서 편의점 있더라. 난 참치마요 먹고 싶어. *둘은 식사를 했다. 다시 상담소에 돌아오니 3시 25분이다.*";
        var target = start.withMinute(25);
        dialogue.chat(session.getId(), input);
        assertThat(session.getSimulatedAt()).isEqualTo(target);
        var character = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(m -> m.getRole() == ChatRole.CHARACTER).findFirst().orElseThrow();
        var user = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(session.getId(), character.getId()).orElseThrow();
        dialogue.reroll(session.getId(), character.getId());
        assertThat(session.getSimulatedAt()).isEqualTo(target);
        dialogue.editMessage(session.getId(), user.getId(), input.replace("25분", "27분"));
        dialogue.regenerate(session.getId(), character.getId());
        assertThat(session.getSimulatedAt()).isEqualTo(target.plusMinutes(2));
        dialogue.chat(session.getId(), "지금은 3시 27분이다.");
        assertThat(session.getSimulatedAt()).isEqualTo(target.plusMinutes(2));
        dialogue.chat(session.getId(), "아 배고프다");
        assertThat(session.getSimulatedAt()).isEqualTo(target.plusMinutes(9));
        verify(gateway, times(2)).generateWorldRoomTurn(argThat(c -> target.equals(c.simulatedAt())), anyString());

        dialogue.chat(session.getId(), "*3일 후, 오후 3시에 잠들어 6시간 잤다.*");
        var wake = LocalDateTime.of(2026, 10, 11, 21, 0);
        assertThat(session.getSimulatedAt()).isEqualTo(wake);
        var sleepReply = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(m -> m.getRole() == ChatRole.CHARACTER).findFirst().orElseThrow();
        dialogue.reroll(session.getId(), sleepReply.getId());
        assertThat(session.getSimulatedAt()).isEqualTo(wake);
        dialogue.chat(session.getId(), "*이후 6시간 더 잤다.*");
        assertThat(session.getSimulatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 12, 3, 0));
        verify(gateway, times(2)).generateWorldRoomTurn(argThat(c -> wake.equals(c.simulatedAt())), anyString());
    }
}
