package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.time.TimeInterpretation;
import com.example.projectmm.domain.chat.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({
        WorldRoomSessionService.class,
        WorldRoomContextBuilder.class,
        WorldRoomDialogueService.class
})
class WorldRoomHistoryIntegrationTest {

    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRoomContextBuilder contextBuilder;
    @Autowired WorldRoomDialogueService dialogueService;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomMessageVariantRepository variants;

    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService memoryRecallService;
    @MockitoBean TimeInterpreter timeInterpreter;

    @Test
    void aiContextUsesSixTurnsWhileUiCanPageAllOriginalHistory() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "history-user", "password", "History User",
                "history-user@example.test", encoder
        ));
        World world = worlds.save(new World(
                member, "Victorian London", "Victorian mystery."
        ));
        WorldConversation room = rooms.save(new WorldConversation(
                world, member, "Long Baker Street Session",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0)
        ));
        WorldRoomIntro intro = intros.save(new WorldRoomIntro(
                room, "Opening", "The case begins.", 0
        ));
        var session = sessionService.start(
                room.getId(), member.getLoginId(), null, intro.getId()
        );

        for (int i = 1; i <= 60; i++) {
            messages.save(new WorldRoomMessage(
                    session,
                    i % 2 == 1 ? ChatRole.USER : ChatRole.CHARACTER,
                    "message-" + i,
                    LocalDateTime.of(1889, 11, 9, 19, 0).plusSeconds(i)
            ));
        }
        messages.flush();

        var context = contextBuilder.buildSession(session.getId());

        assertThat(context.recentMessages()).hasSize(12);
        assertThat(context.recentMessages().get(0).content()).isEqualTo("message-49");
        assertThat(context.recentMessages().get(11).content()).isEqualTo("message-60");

        // Reroll must not include its target or future messages.
        var boundary = messages.findTop50BySessionIdOrderByIdDesc(session.getId()).stream()
                .filter(message -> message.getContent().equals("message-49")).findFirst().orElseThrow();
        var before = contextBuilder.buildSessionBeforeMessage(session.getId(), boundary.getId());
        assertThat(before.recentMessages()).hasSize(12);
        assertThat(before.recentMessages().getFirst().content()).isEqualTo("message-37");
        assertThat(before.recentMessages().getLast().content()).isEqualTo("message-48");
        assertThat(messages.count()).isEqualTo(60);

        var firstPage = dialogueService.history(session.getId(), null);

        assertThat(firstPage).hasSize(15);
        assertThat(firstPage.get(0).getContent()).isEqualTo("message-46");
        assertThat(firstPage.get(14).getContent()).isEqualTo("message-60");

        Long beforeId = firstPage.get(0).getId();
        var olderPage = dialogueService.history(session.getId(), beforeId);

        assertThat(olderPage).hasSize(15);
        assertThat(olderPage.get(0).getContent()).isEqualTo("message-31");
        assertThat(olderPage.get(14).getContent()).isEqualTo("message-45");
    }
    private Member listOwner(String login) {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");
        when(timeInterpreter.interpret(anyString(), any(LocalDateTime.class))).thenReturn(TimeInterpretation.noMatch());
        when(gateway.generateWorldRoomTurn(any(), anyString())).thenReturn(
                new com.example.projectmm.domain.dialogue.WorldRoomTurn("Reply", com.example.projectmm.domain.dialogue.WorldRoomCastChange.none(), java.util.List.of(), 0));
        return members.save(Member.createLocal(login,"password","Tester",login+"@example.test",encoder));
    }
    private WorldRoomSession listPocket(Member owner, int year) {
        var at = LocalDateTime.of(year,1,1,12,0);
        var world = worlds.save(new World(owner,"World","Setting"));
        var room = rooms.save(new WorldConversation(world,owner,"Pocket",WorldConversation.Visibility.PRIVATE,at));
        var intro = intros.save(new WorldRoomIntro(room,"Start","Opening",0,at,"Home",java.util.List.of("normal"),java.util.List.of("phone")));
        return sessionService.start(room.getId(),owner.getLoginId(),null,intro.getId());
    }
    private void assertOrder(Member owner, WorldRoomSession... expected) {
        assertThat(sessionService.pockets(owner.getLoginId())).extracting(WorldRoomSession::getId)
                .containsExactly(java.util.Arrays.stream(expected).map(WorldRoomSession::getId).toArray(Long[]::new));
    }
    @Test void latestProgressWinsAcrossDifferentStoryDatesWhileViewingAndSettingsDoNotReorder() {
        var owner = listOwner("progress-order");
        var future = listPocket(owner,2099);
        var past = listPocket(owner,1889);
        assertOrder(owner,past,future);
        sessionService.pocket(future.getId(),owner.getLoginId());
        dialogueService.history(future.getId(),null);
        future.markPresent(LocalDateTime.now().plusHours(2));
        sessionService.updatePocketSettings(future.getId(),owner.getLoginId(),"note","guide",false);
        assertOrder(owner,past,future);
        dialogueService.chat(future.getId(),"Continue");
        assertOrder(owner,future,past);
        future.updateSimulatedAt(LocalDateTime.of(1800,1,1,0,0));
        assertOrder(owner,future,past);
    }
    @Test void continuationAndRerollMovePocketUpButSelectingOrEditingOldReplyDoesNot() {
        var owner = listOwner("progress-actions");
        var first = listPocket(owner,2000); var second = listPocket(owner,2100);
        dialogueService.chat(first.getId(),"Hello");
        var reply = messages.findTopBySessionIdOrderByIdDesc(first.getId()).orElseThrow();
        dialogueService.chat(second.getId(),"Hello"); assertOrder(owner,second,first);
        dialogueService.reroll(first.getId(),reply.getId()); assertOrder(owner,first,second);
        dialogueService.continueScene(second.getId()); assertOrder(owner,second,first);
        dialogueService.selectVariant(first.getId(),reply.getId(),1); assertOrder(owner,second,first);
        dialogueService.editMessage(first.getId(),reply.getId(),"Corrected prose"); assertOrder(owner,second,first);
    }
    @Test void legacyPocketsUseReplyAndRerollTimesAndNeverUserMessageOrPresenceTimes() {
        var owner = listOwner("progress-legacy");
        var first = listPocket(owner,2099); var second = listPocket(owner,1889);
        org.springframework.test.util.ReflectionTestUtils.setField(first,"lastProgressedAt",null);
        org.springframework.test.util.ReflectionTestUtils.setField(second,"lastProgressedAt",null);
        var at = LocalDateTime.of(2025,1,1,12,0);
        var source = messages.save(new WorldRoomMessage(first,ChatRole.CHARACTER,"First",at));
        variants.save(new WorldRoomMessageVariant(source,1,"First",at));
        messages.save(new WorldRoomMessage(second,ChatRole.CHARACTER,"Second",at.plusDays(1)));
        messages.save(new WorldRoomMessage(first,ChatRole.USER,"Unanswered user input",at.plusYears(20)));
        first.markPresent(at.plusYears(30));
        assertOrder(owner,second,first);
        messages.save(new WorldRoomMessage(first,ChatRole.CHARACTER,"Legacy reply without a variant",at.plusDays(2)));
        assertOrder(owner,first,second);
        messages.save(new WorldRoomMessage(second,ChatRole.CHARACTER,"Later legacy reply",at.plusDays(3)));
        assertOrder(owner,second,first);
        variants.save(new WorldRoomMessageVariant(source,2,"Rerolled",at.plusDays(4)));
        assertOrder(owner,first,second);
    }
    @Test void failedGenerationDoesNotChangeProgressOrder() {
        var owner = listOwner("progress-failed");
        var first = listPocket(owner,2099); var second = listPocket(owner,1889);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenThrow(new IllegalStateException("Provider unavailable"));
        assertThatThrownBy(() -> dialogueService.chat(first.getId(),"Hello")).isInstanceOf(IllegalStateException.class);
        assertOrder(owner,second,first);
    }

}
