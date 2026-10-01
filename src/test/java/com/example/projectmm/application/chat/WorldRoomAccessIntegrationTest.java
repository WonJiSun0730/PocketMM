package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.WorldConversation;
import com.example.projectmm.domain.chat.WorldConversationRepository;
import com.example.projectmm.domain.chat.WorldRoomIntro;
import com.example.projectmm.domain.chat.WorldRoomIntroRepository;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.world.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({
        WorldRoomService.class,
        WorldRoomSessionService.class
})
class WorldRoomAccessIntegrationTest {

    @Autowired WorldRoomService roomService;
    @Autowired WorldRoomSessionService sessionService;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;

    @Test
    void privateRoomIsOwnerOnlyButPublicRoomAllowsAnotherMemberToPlay() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member owner = members.save(Member.createLocal(
                "victorian-owner", "password", "Room Owner",
                "victorian-owner@example.test", encoder
        ));
        Member outsider = members.save(Member.createLocal(
                "victorian-reader", "password", "Room Reader",
                "victorian-reader@example.test", encoder
        ));

        World world = worlds.save(new World(
                owner, "Victorian London", "Victorian mystery."
        ));

        WorldConversation privateRoom = rooms.save(new WorldConversation(
                world, owner, "Private Baker Street",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0)
        ));
        WorldRoomIntro privateIntro = intros.save(new WorldRoomIntro(
                privateRoom, "Private Intro", "Private opening scene.", 0
        ));

        assertThat(roomService.accessibleRoom(privateRoom.getId(), owner.getLoginId()).getId())
                .isEqualTo(privateRoom.getId());

        assertThatThrownBy(() ->
                roomService.accessibleRoom(privateRoom.getId(), outsider.getLoginId())
        ).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));

        assertThatThrownBy(() ->
                sessionService.start(
                        privateRoom.getId(),
                        outsider.getLoginId(),
                        null,
                        privateIntro.getId()
                )
        ).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));

        WorldConversation publicRoom = rooms.save(new WorldConversation(
                world, owner, "Public Baker Street",
                WorldConversation.Visibility.PUBLIC,
                LocalDateTime.of(1889, 11, 9, 20, 0)
        ));
        WorldRoomIntro publicIntro = intros.save(new WorldRoomIntro(
                publicRoom, "Public Intro", "Public opening scene.", 0
        ));

        assertThat(roomService.accessibleRoom(publicRoom.getId(), outsider.getLoginId()).getId())
                .isEqualTo(publicRoom.getId());

        var outsiderSession = sessionService.start(
                publicRoom.getId(),
                outsider.getLoginId(),
                null,
                publicIntro.getId()
        );

        assertThat(outsiderSession.getMember().getId()).isEqualTo(outsider.getId());
        assertThat(outsiderSession.getRoom().getId()).isEqualTo(publicRoom.getId());
        assertThat(outsiderSession.getIntro().getId()).isEqualTo(publicIntro.getId());
    }
}
