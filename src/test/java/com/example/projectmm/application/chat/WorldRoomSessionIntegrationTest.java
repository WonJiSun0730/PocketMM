package com.example.projectmm.application.chat;

import com.example.projectmm.domain.chat.WorldConversation;
import com.example.projectmm.domain.chat.WorldConversationRepository;
import com.example.projectmm.domain.chat.WorldRoomIntro;
import com.example.projectmm.domain.chat.WorldRoomIntroRepository;
import com.example.projectmm.domain.chat.WorldRoomSessionCastRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(WorldRoomSessionService.class)
class WorldRoomSessionIntegrationTest {

    @Autowired WorldRoomSessionService sessions;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldSystemRepository systems;
    @Autowired WorldRoomSessionCastRepository sessionCasts;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.example.projectmm.domain.chat.WorldRoomSessionRepository sessionRepository;

    @Test
    void initialIntroAndSceneAreStoredIndependentlyOfProgressAndLaterIntroEdits() {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");
        var member = members.save(Member.createLocal("initial", "password", "Initial", "initial@example.test", encoder));
        var world = worlds.save(new World(member, "TestWorld", "A world"));
        var at = LocalDateTime.of(2026, 10, 8, 15, 5);
        var room = rooms.save(new WorldConversation(world, member, "Room", WorldConversation.Visibility.PRIVATE, at));
        var intro = intros.save(new WorldRoomIntro(room, "Free start", "Start freely", 0, at, "대기실",
                java.util.List.of("피곤함"), java.util.List.of("노트")));
        var session = sessions.start(room.getId(), member.getLoginId(), null, intro.getId());
        session.updateSimulatedAt(at.plusMinutes(32));
        intro.update("New intro", "Changed", 0, at.plusDays(10), "학교", java.util.List.of(), java.util.List.of("가방"));
        entityManager.flush();
        entityManager.clear();
        var loaded = sessionRepository.findById(session.getId()).orElseThrow();
        var response = com.example.projectmm.api.world.WorldRoomSessionResponse.from(loaded);
        assertThat(response.introTitle()).isEqualTo("Free start");
        assertThat(response.introContent()).isEqualTo("Start freely");
        assertThat(response.simulatedAt()).isEqualTo(at.plusMinutes(32));
        assertThat(response.initialScene().simulatedAt()).isEqualTo(at);
        assertThat(response.initialScene().location()).isEqualTo("대기실");
        assertThat(response.initialScene().conditions()).containsExactly("피곤함");
        assertThat(response.initialScene().inventory()).containsExactly("노트");
        sessions.deletePocket(loaded.getId(), member.getLoginId());
        entityManager.flush();
        assertThat(sessionRepository.findById(loaded.getId())).isEmpty();
    }

    @Test
    void victorianSessionStartsWithSelectedIntroNoActiveCastAndInitialSystemValue() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");

        Member member = members.save(Member.createLocal(
                "eleanor",
                "password",
                "Eleanor Watson",
                "eleanor@example.test",
                encoder
        ));

        World world = worlds.save(new World(
                member,
                "Victorian London",
                "A fog-bound Victorian London mystery world."
        ));

        WorldConversation room = rooms.save(new WorldConversation(
                world,
                member,
                "The Baker Street Case",
                WorldConversation.Visibility.PRIVATE,
                LocalDateTime.of(1889, 11, 9, 19, 0)
        ));

        WorldRoomIntro intro = intros.save(new WorldRoomIntro(
                room,
                "Fog over Baker Street",
                "A dense fog hangs over Baker Street as a new case begins.",
                0
        ));

        WorldSystem tension = systems.save(new WorldSystem(
                world,
                "Case Tension",
                "Increase when danger, uncertainty, or pressure escalates.",
                0,
                100,
                20,
                0
        ));

        var session = sessions.start(
                room.getId(),
                member.getLoginId(),
                null,
                intro.getId()
        );

        assertThat(session.getIntro().getId()).isEqualTo(intro.getId());
        assertThat(session.getIntro().getTitle()).isEqualTo("Fog over Baker Street");
        assertThat(session.getIntro().getContent())
                .isEqualTo("A dense fog hangs over Baker Street as a new case begins.");

        assertThat(sessionCasts.findBySessionIdAndActiveTrueOrderByEnteredAtAscIdAsc(session.getId()))
                .isEmpty();

        assertThat(sessions.systemStates(session.getId()))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.getSystem().getId()).isEqualTo(tension.getId());
                    assertThat(state.getCurrentValue()).isEqualTo(20);
                });
    }
}
