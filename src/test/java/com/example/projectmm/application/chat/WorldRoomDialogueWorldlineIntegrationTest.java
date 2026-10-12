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
        WorldRoomPlayerStateService.class,
        WorldRoomContextBuilder.class,
        WorldRoomDialogueService.class
})
class WorldRoomDialogueWorldlineIntegrationTest {

    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRoomPlayerStateService playerStateService;
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
    @Autowired jakarta.persistence.EntityManager entityManager;

    private WorldRoomSession automaticProgressionSession(String login) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");
        var member = members.save(Member.createLocal(login, "password", "Millie", login + "@example.test", encoder));
        var world = worlds.save(new World(member, "Kitchen", "Preparing food."));
        var room = rooms.save(new WorldConversation(world, member, "Kitchen",
                WorldConversation.Visibility.PRIVATE, LocalDateTime.of(2026, 11, 4, 21, 0)));
        var intro = intros.save(new WorldRoomIntro(room, "Start", "Prepare ingredients.", 0));
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(
                new WorldRoomTurn("*준비가 이어진다.*", WorldRoomCastChange.none(), List.of(), 0));
        return sessionService.start(room.getId(), login, null, intro.getId());
    }

    @Test
    void automaticProgressionPersistsThresholdsRerollsDeletionAndUserInputReset() {
        var session = automaticProgressionSession("automatic-scope");
        Long sessionId = session.getId();
        for (int number = 1; number <= 8; number++) {
            dialogueService.continueScene(sessionId);
            entityManager.flush();
            entityManager.clear();
            assertThat(messages.findTopBySessionIdOrderByIdDesc(sessionId).orElseThrow()
                    .getAutomaticContinuationNo()).isEqualTo(number);
            verify(gateway).generateWorldRoomTurn(any(), eq(
                    com.example.projectmm.application.dialogue.WorldRoomContinuationPrompt.build(false, number)));
        }
        var last = messages.findTopBySessionIdOrderByIdDesc(sessionId).orElseThrow();
        dialogueService.reroll(sessionId, last.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(messages.findById(last.getId()).orElseThrow().getAutomaticContinuationNo()).isEqualTo(8);
        assertThat(variants.findByMessageIdAndVariantNo(last.getId(), 2).orElseThrow()
                .getEvaluationContextJson()).contains("consecutiveNo=8", "playerScope=HIGH");
        var seventh = messages.findTop50BySessionIdOrderByIdDesc(sessionId).stream()
                .filter(m -> Integer.valueOf(7).equals(m.getAutomaticContinuationNo())).findFirst().orElseThrow();
        dialogueService.deleteMessageAndFollowing(sessionId, seventh.getId());
        dialogueService.continueScene(sessionId);
        assertThat(messages.findTopBySessionIdOrderByIdDesc(sessionId).orElseThrow().getAutomaticContinuationNo())
                .isEqualTo(7);
        dialogueService.chat(sessionId, "*밀리는 재료 손질을 마쳤다.*");
        assertThat(messages.findTopBySessionIdOrderByIdDesc(sessionId).orElseThrow().getAutomaticContinuationNo())
                .isNull();
        dialogueService.continueScene(sessionId);
        assertThat(messages.findTopBySessionIdOrderByIdDesc(sessionId).orElseThrow().getAutomaticContinuationNo())
                .isEqualTo(1);
        verify(gateway, times(2)).generateWorldRoomTurn(any(), eq(
                com.example.projectmm.application.dialogue.WorldRoomContinuationPrompt.build(false, 1)));
    }

    @Test
    void noControlOverridesHighAutomaticScopeAndFailedGenerationDoesNotAdvanceNumber() {
        var session = automaticProgressionSession("automatic-strict");
        sessionService.updatePocketSettings(session.getId(), session.getMember().getLoginId(), "", "", true);
        for (int number = 1; number <= 6; number++) dialogueService.continueScene(session.getId());
        verify(gateway).generateWorldRoomTurn(argThat(context -> context.noControlPlayer()), eq(
                com.example.projectmm.application.dialogue.WorldRoomContinuationPrompt.build(true, 6)));
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenThrow(new IllegalStateException("Provider unavailable"));
        assertThatThrownBy(() -> dialogueService.continueScene(session.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(messages.findTopBySessionIdOrderByIdDesc(session.getId()).orElseThrow().getAutomaticContinuationNo())
                .isEqualTo(6);
    }

    @BeforeEach
    void defaultTimeInterpretation() {
        when(timeInterpreter.interpret(anyString(), any(LocalDateTime.class)))
                .thenReturn(TimeInterpretation.noMatch());
    }

    @Test
    void compoundSleepTimeSurvivesRerollAndEditedRegenerationWithoutDoubleElapsedTime() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");
        Member member = members.save(Member.createLocal("compound-time-user", "password",
                "Compound Time", "compound-time@example.test", encoder));
        World world = worlds.save(new World(member, "Compound Time", "Time test."));
        WorldConversation room = rooms.save(new WorldConversation(world, member, "Room",
                WorldConversation.Visibility.PRIVATE, LocalDateTime.of(2026, 1, 8, 12, 0)));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(room, "Start", "Start scene.", 0));
        var session = sessionService.start(room.getId(), member.getLoginId(), null, intro.getId());
        var before = session.getSimulatedAt();
        var target = before.plusDays(3).withHour(21).withMinute(0).withSecond(0).withNano(0);
        var rule = new com.example.projectmm.application.time.RuleBasedTimeInterpreter();
        when(timeInterpreter.interpret(anyString(), any(LocalDateTime.class)))
                .thenAnswer(call -> rule.interpret(call.getArgument(0), call.getArgument(1)));
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(
                new WorldRoomTurn("Woke up.", WorldRoomCastChange.none(), List.of(), 3600));

        dialogueService.chat(session.getId(), "3일 후, 오후 3시에 잠들어 6시간 잤다");
        assertThat(session.getSimulatedAt()).isEqualTo(target);
        var character = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.CHARACTER).findFirst().orElseThrow();
        var user = messages.findTopBySessionIdAndIdLessThanOrderByIdDesc(session.getId(), character.getId())
                .orElseThrow();
        dialogueService.reroll(session.getId(), character.getId());
        assertThat(session.getSimulatedAt()).isEqualTo(target);

        dialogueService.editMessage(session.getId(), user.getId(),
                "3일 후, 오후 3시에 잠들어 7시간 잤다");
        dialogueService.regenerate(session.getId(), character.getId());
        assertThat(session.getSimulatedAt()).isEqualTo(target.plusHours(1));
        verify(gateway, times(2)).generateWorldRoomTurn(
                argThat(context -> target.equals(context.simulatedAt())), anyString());
        verify(gateway).generateWorldRoomTurn(
                argThat(context -> target.plusHours(1).equals(context.simulatedAt())), anyString());
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
    void ordinaryUserTurnAdvancesPocketClockByGeneratedNarrativeElapsedTime() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "natural-time-user", "password", "Natural Time User",
                "natural-time-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Natural Time World", "Time test."));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Natural Time Room",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 7, 12, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(room, "Start", "Start scene.", 0));
        var session = sessionService.start(room.getId(), member.getLoginId(), null, intro.getId());
        var before = session.getSimulatedAt();

        when(gateway.generateWorldRoomTurn(any(), eq("차를 한 모금 마시며 이야기를 이어간다.")))
                .thenReturn(new WorldRoomTurn(
                        "*짧은 대화가 이어진다.*",
                        WorldRoomCastChange.none(),
                        List.of(),
                        95
                ));

        dialogueService.chat(session.getId(), "차를 한 모금 마시며 이야기를 이어간다.");

        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(before.plusSeconds(95));
    }

    @Test
    void continueSceneAdvancesPocketClockByNarrativeDevelopment() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "continue-time-user", "password", "Continue Time User",
                "continue-time-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Continue Time World", "Time test."));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Continue Time Room",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 7, 18, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(room, "Start", "Start scene.", 0));
        var session = sessionService.start(room.getId(), member.getLoginId(), null, intro.getId());
        var before = session.getSimulatedAt();

        when(gateway.generateWorldRoomTurn(any(), contains("developed story beat")))
                .thenReturn(new WorldRoomTurn(
                        "*식사를 마친 뒤 일행은 역까지 걸어간다.*",
                        WorldRoomCastChange.none(),
                        List.of(),
                        1_800
                ));

        dialogueService.continueScene(session.getId());

        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(before.plusMinutes(30));
    }

    @Test
    void explicitUserTimeChangeDoesNotAlsoApplyGeneratedNarrativeElapsedTime() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "explicit-only-user", "password", "Explicit Only User",
                "explicit-only-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Explicit Only World", "Time test."));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Explicit Only Room",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 7, 9, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(room, "Start", "Start scene.", 0));
        var session = sessionService.start(room.getId(), member.getLoginId(), null, intro.getId());

        var before = session.getSimulatedAt();
        var after = before.plusDays(3);
        when(timeInterpreter.interpret(anyString(), eq(before)))
                .thenReturn(TimeInterpretation.matched(
                        TimeInterpretation.Interpreter.RULE,
                        null,
                        TimeChange.changed(before, after, "3일 후")
                ));
        when(gateway.generateWorldRoomTurn(any(), anyString()))
                .thenReturn(new WorldRoomTurn(
                        "*3일 뒤의 장면으로 넘어간다.*",
                        WorldRoomCastChange.none(),
                        List.of(),
                        3_600
                ));

        dialogueService.chat(session.getId(), "[OOC] 3일 후로 진행.");

        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(after);
    }

    @Test
    void rerollVariantSelectionAndDeletionRestorePocketTimeWorldline() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "time-worldline-user", "password", "Time Worldline User",
                "time-worldline-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Time Worldline", "Time test."));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Time Worldline Room",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 7, 20, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(room, "Start", "Start scene.", 0));
        var session = sessionService.start(room.getId(), member.getLoginId(), null, intro.getId());
        var baseline = session.getSimulatedAt();

        when(gateway.generateWorldRoomTurn(any(), eq("이야기를 이어간다.")))
                .thenReturn(new WorldRoomTurn(
                        "*잠시 이야기가 이어진다.*",
                        WorldRoomCastChange.none(),
                        List.of(),
                        120
                ));

        dialogueService.chat(session.getId(), "이야기를 이어간다.");
        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(baseline.plusMinutes(2));

        var characterMessage = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.CHARACTER)
                .findFirst()
                .orElseThrow();

        when(gateway.generateWorldRoomTurn(
                any(),
                argThat(prompt -> prompt != null && prompt.contains("[Previous response variant]"))
        )).thenReturn(new WorldRoomTurn(
                "*이번에는 긴 대화를 나눈다.*",
                WorldRoomCastChange.none(),
                List.of(),
                600
        ));

        dialogueService.reroll(session.getId(), characterMessage.getId());

        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(baseline.plusMinutes(10));

        dialogueService.selectVariant(session.getId(), characterMessage.getId(), 1);
        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(baseline.plusMinutes(2));

        dialogueService.selectVariant(session.getId(), characterMessage.getId(), 2);
        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(baseline.plusMinutes(10));

        dialogueService.deleteMessageAndFollowing(session.getId(), characterMessage.getId());

        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(baseline);
    }

    @Test
    void manualRuntimeTimeEditAndNarrativeProgressionShareOnePocketClock() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "single-clock-user", "password", "Single Clock User",
                "single-clock-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Single Clock World", "Time test."));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Single Clock Room",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 7, 9, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(room, "Start", "Start scene.", 0));
        var session = sessionService.start(room.getId(), member.getLoginId(), null, intro.getId());

        var manuallySet = LocalDateTime.of(2027, 1, 15, 14, 30);
        playerStateService.update(
                session.getId(),
                member.getLoginId(),
                manuallySet,
                "도서관",
                List.of("피곤함"),
                List.of("책")
        );

        when(gateway.generateWorldRoomTurn(
                argThat(context -> context != null && manuallySet.equals(context.simulatedAt())),
                eq("조금 더 이야기를 나눈다.")
        )).thenReturn(new WorldRoomTurn(
                "*몇 분 동안 대화가 이어진다.*",
                WorldRoomCastChange.none(),
                List.of(),
                300
        ));

        dialogueService.chat(session.getId(), "조금 더 이야기를 나눈다.");

        assertThat(sessions.findById(session.getId()).orElseThrow().getSimulatedAt())
                .isEqualTo(manuallySet.plusMinutes(5));
        verify(gateway).generateWorldRoomTurn(
                argThat(context -> manuallySet.equals(context.simulatedAt())),
                eq("조금 더 이야기를 나눈다.")
        );
    }

    @Test
    void pocketSettingsReachDialogueContextAndNoControlContinuationPrompt() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "pocket-settings-user", "password", "Pocket Settings User",
                "pocket-settings-user@example.test", encoder
        ));
        World world = worlds.save(new World(member, "Settings World", "Settings test."));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Settings Room",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(2026, 10, 7, 21, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(room, "Start", "Start scene.", 0));
        var session = sessionService.start(room.getId(), member.getLoginId(), null, intro.getId());

        sessionService.updatePocketSettings(
                session.getId(),
                member.getLoginId(),
                "이 장면에서 계속 참고할 메모",
                "잔잔한 분위기를 유지한다.",
                true
        );

        when(gateway.generateWorldRoomTurn(
                argThat(context -> context != null
                        && "이 장면에서 계속 참고할 메모".equals(context.userNote())
                        && "잔잔한 분위기를 유지한다.".equals(context.pocketPlayGuide())
                        && context.noControlPlayer()),
                contains("without controlling the player character")
        )).thenReturn(new WorldRoomTurn(
                "*주변 인물들의 대화가 이어진다.*",
                WorldRoomCastChange.none(),
                List.of(),
                30
        ));

        dialogueService.continueScene(session.getId());

        verify(gateway).generateWorldRoomTurn(
                argThat(context -> context != null
                        && "이 장면에서 계속 참고할 메모".equals(context.userNote())
                        && "잔잔한 분위기를 유지한다.".equals(context.pocketPlayGuide())
                        && context.noControlPlayer()),
                argThat(prompt -> prompt != null
                        && prompt.contains("without controlling the player character")
                        && prompt.contains("Do not invent any player dialogue"))
        );

        var reply = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getRole() == ChatRole.CHARACTER).findFirst().orElseThrow();
        dialogueService.reroll(session.getId(), reply.getId());
        verify(gateway, times(2)).generateWorldRoomTurn(
                argThat(context -> context != null && context.noControlPlayer()),
                contains("without controlling the player character"));

        sessionService.updatePocketSettings(session.getId(), member.getLoginId(), "", "", false);
        when(gateway.generateWorldRoomTurn(argThat(context -> context != null && !context.noControlPlayer()), anyString()))
                .thenReturn(new WorldRoomTurn("*사용자가 정한 준비 과정과 주변 풍경이 이어진다.*", WorldRoomCastChange.none(), List.of(), 30));
        dialogueService.reroll(session.getId(), reply.getId());
        dialogueService.continueScene(session.getId());
        verify(gateway, times(2)).generateWorldRoomTurn(
                argThat(context -> context != null && !context.noControlPlayer()),
                argThat(prompt -> prompt.contains("Elaborate only the concrete steps")
                        && prompt.contains("Never invent new player dialogue")
                        && prompt.contains("Stop before a fresh player answer or decision")
                        && !prompt.contains("without controlling the player character")));
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
