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

    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService memoryRecallService;
    @MockitoBean TimeInterpreter timeInterpreter;

    @Test
    void aiContextUsesLatestFiftyMessagesWhileUiCanPageOlderHistory() {
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

        assertThat(context.recentMessages()).hasSize(50);
        assertThat(context.recentMessages().get(0).content()).isEqualTo("message-11");
        assertThat(context.recentMessages().get(49).content()).isEqualTo("message-60");

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
}
