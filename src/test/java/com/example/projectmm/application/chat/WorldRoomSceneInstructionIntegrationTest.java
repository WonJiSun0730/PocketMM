package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
import com.example.projectmm.domain.dialogue.WorldRoomTurn;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.time.TimeInterpretation;
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
class WorldRoomSceneInstructionIntegrationTest {
    @org.junit.jupiter.api.BeforeEach
    void approveContinuityForExistingScenario() {
        com.example.projectmm.application.dialogue.SceneContinuityTestSupport.approveUnchanged(gateway);
    }

    @Autowired WorldRoomSessionService sessions;
    @Autowired WorldRoomDialogueService dialogue;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomPlayerStateRepository states;
    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService memories;
    @MockitoBean TimeInterpreter time;

    @Test
    void resetPatchRerollAndEditedInstructionPreserveTheRequestedScope() {
        var session = fixture(true);
        when(time.interpret(anyString(), any())).thenReturn(TimeInterpretation.noMatch());
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(
                new WorldRoomTurn("설정에 맞춘 장면입니다.", WorldRoomCastChange.none(), List.of(), 420));
        String reset = "OOC: 진행중인 롤플 중단하고 새 상황에서 시작해. 오늘은 주말 상담을 잡았다. 해원은 편한 사복을 입고 상담에 가는길이다.";
        when(gateway.setupWorldRoomScene(any(), eq(reset))).thenReturn("""
                {"mode":"RESET","simulatedAt":"2026-10-10T15:00:00","location":"상담 가는 길",
                 "inventory":["지갑","휴대폰"],"conditions":[]}
                """);
        dialogue.chat(session.getId(), reset);
        var target = lastCharacter(session);
        assertResetState(session);
        dialogue.reroll(session.getId(), target.getId());
        assertResetState(session);
        verify(gateway, times(1)).setupWorldRoomScene(any(), eq(reset));
        dialogue.selectVariant(session.getId(), target.getId(), 1);
        assertResetState(session);

        String patch = "OOC: 소지품만 물병으로 바꿔. 다른 상태는 유지해.";
        when(gateway.setupWorldRoomScene(any(), eq(patch))).thenReturn("""
                {"mode":"PATCH","simulatedAt":null,"location":null,"inventory":["물병"],"conditions":null}
                """);
        dialogue.chat(session.getId(), patch);
        assertThat(state(session).getInventory()).containsExactly("물병");
        assertThat(state(session).getLocation()).isEqualTo("상담 가는 길");
        assertThat(session.getSimulatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 15, 0));
        var patchReply = lastCharacter(session);
        var user = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(session.getId(), patchReply.getId()).orElseThrow();
        String edited = "OOC: 장소만 학교로 바꿔. 나머지는 유지해.";
        when(gateway.setupWorldRoomScene(any(), eq(edited))).thenReturn("""
                {"mode":"PATCH","simulatedAt":null,"location":"학교","inventory":null,"conditions":null}
                """);
        dialogue.editMessage(session.getId(), user.getId(), edited);
        dialogue.regenerate(session.getId(), patchReply.getId());
        assertThat(state(session).getLocation()).isEqualTo("학교");
        // Regeneration starts from the state before the edited user instruction.
        assertThat(state(session).getInventory()).containsExactly("지갑", "휴대폰");
        assertThat(state(session).getConditions()).isEmpty();
        assertThat(session.getSimulatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 15, 0));
    }

    @Test
    void relativeTimeInAnOocPatchIsAppliedOnlyOnce() {
        var session = fixture(true);
        String input = "OOC: 시간만 3일 뒤로 바꿔.";
        when(gateway.setupWorldRoomScene(any(), eq(input))).thenReturn("""
                {"mode":"PATCH","simulatedAt":"2026-10-11T15:05:00","location":null,"inventory":null,"conditions":null}
                """);
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(new WorldRoomTurn("설정했습니다.", WorldRoomCastChange.none()));
        dialogue.chat(session.getId(), input);
        assertThat(session.getSimulatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 11, 15, 5));
        verifyNoInteractions(time);
        assertThat(state(session).getInventory()).containsExactly("가방");
        assertThat(state(session).getConditions()).containsExactly("반깁스");
    }

    @Test
    void ordinaryOocQuestionDoesNotReconstructInventoryOrConditions() {
        var session = fixture(true);
        when(time.interpret(anyString(), any())).thenReturn(TimeInterpretation.noMatch());
        when(gateway.setupWorldRoomScene(any(), anyString())).thenReturn("{\"mode\":\"NONE\"}");
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(new WorldRoomTurn("설명입니다.", WorldRoomCastChange.none()));
        dialogue.chat(session.getId(), "OOC: 지금 시스템이 어떻게 작동해?");
        assertThat(state(session).getInventory()).containsExactly("가방");
        assertThat(state(session).getConditions()).containsExactly("반깁스");
        assertThat(state(session).getLocation()).isEqualTo("대기실");
    }

    @Test
    void missingOpeningFieldsAreInferredOnceBeforeUserTimeProgressionAndStoredAsInitialState() {
        var session = fixture(false);
        var start = LocalDateTime.of(2026, 10, 8, 15, 5);
        when(gateway.initializeWorldRoomScene(any(), eq(true), anyString())).thenReturn("""
                {"simulatedAt":"2026-10-08T15:05:00","location":"상담소 대기실",
                 "inventory":["가방","지갑","휴대폰"],"conditions":[]}
                """);
        var rule = new com.example.projectmm.application.time.RuleBasedTimeInterpreter();
        when(time.interpret(anyString(), any())).thenAnswer(call -> rule.interpret(call.getArgument(0), call.getArgument(1)));
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(new WorldRoomTurn("장면입니다.", WorldRoomCastChange.none()));
        dialogue.chat(session.getId(), "3일 후, 오후 3시에 잠들어 6시간 잤다");
        assertThat(session.getSimulatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 11, 21, 0));
        assertThat(session.getInitialSimulatedAt()).isEqualTo(start);
        assertThat(session.getInitialLocation()).isEqualTo("상담소 대기실");
        assertThat(session.getInitialInventory()).containsExactly("가방", "지갑", "휴대폰");
        dialogue.reroll(session.getId(), lastCharacter(session).getId());
        verify(gateway, times(1)).initializeWorldRoomScene(any(), anyBoolean(), anyString());
        assertThat(messages.findTop50BySessionIdOrderByIdDesc(session.getId())).hasSize(2);
    }

    private WorldRoomPlayerState state(WorldRoomSession s) { return states.findBySessionId(s.getId()).orElseThrow(); }
    private WorldRoomMessage lastCharacter(WorldRoomSession s) {
        return messages.findTop50BySessionIdOrderByIdDesc(s.getId()).stream().filter(m -> m.getRole() == ChatRole.CHARACTER).findFirst().orElseThrow();
    }
    private void assertResetState(WorldRoomSession s) {
        assertThat(state(s).getLocation()).isEqualTo("상담 가는 길");
        assertThat(state(s).getInventory()).containsExactly("지갑", "휴대폰");
        assertThat(state(s).getConditions()).isEmpty();
        assertThat(s.getSimulatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 15, 0));
    }
    private WorldRoomSession fixture(boolean configured) {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");
        var member = members.save(Member.createLocal("setup", "password", "Setup", "setup@example.test", encoder));
        var world = worlds.save(new World(member, "트라우마 상담 전담", "Modern counseling world"));
        var at = LocalDateTime.of(2026, 10, 8, 15, 5);
        var room = rooms.save(new WorldConversation(world, member, "상담", WorldConversation.Visibility.PRIVATE, at));
        var intro = intros.save(new WorldRoomIntro(room, "자유시작", "자유로이 전개하시오.", 0,
                configured ? at : null, configured ? "대기실" : null,
                configured ? List.of("반깁스") : List.of(), configured ? List.of("가방") : List.of()));
        return sessions.start(room.getId(), member.getLoginId(), null, intro.getId());
    }
}
