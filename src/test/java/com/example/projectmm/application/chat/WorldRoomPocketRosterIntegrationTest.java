package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.character.CharacterProfileRepository;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.WorldRoomCastChange;
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
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({WorldRoomSessionService.class, WorldRoomContextBuilder.class})
class WorldRoomPocketRosterIntegrationTest {
    @Autowired WorldRoomSessionService service;
    @Autowired WorldRoomContextBuilder context;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired CharacterRepository characters;
    @Autowired CharacterProfileRepository profiles;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldRoomSessionRepository sessions;
    @Autowired WorldConversationCastRepository roomCasts;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomMessageVariantRepository variants;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean MemoryRecallService memories;

    @Test
    void eachPocketKeepsItsCreationRosterAfterWorldCharactersAreAddedAndRemoved() {
        var f = fixture();
        var marbas = characters.save(new Character("Marbas", f.member()));
        marbas.assignWorld(f.world());
        profiles.save(new CharacterProfile(marbas, "Counselor", "Marbas profile", null, null, "British English", null, null));
        var first = start(f);
        var yumi = characters.save(new Character("Yumi", f.member()));
        yumi.assignWorld(f.world());
        profiles.save(new CharacterProfile(yumi, "Student", "Yumi profile", null, null, "Korean", null, null));
        var second = start(f);
        marbas.assignWorld(null);
        var third = start(f);
        entityManager.flush();
        entityManager.clear();

        assertRoster(first, "Marbas");
        assertRoster(second, "Marbas", "Yumi");
        assertRoster(third, "Yumi");
        assertThat(context.buildSession(second.getId()).roomCast()).extracting(c -> c.profile().rawDescription())
                .containsExactly("Marbas profile", "Yumi profile");
        assertThat(service.activeCast(first.getId())).isEmpty();
        assertThat(service.applyCastChange(first.getId(), enter(yumi))).isEqualTo(WorldRoomCastChange.none());
        assertThat(service.applyCastChange(third.getId(), enter(marbas))).isEqualTo(WorldRoomCastChange.none());
        assertThat(service.applyCastChange(second.getId(), enter(marbas)).enterCharacterIds())
                .containsExactly(marbas.getId());

        var before = service.captureCastState(second.getId());
        service.applyCastChange(second.getId(), new WorldRoomCastChange(List.of(yumi.getId()), List.of(marbas.getId())));
        var message = messages.save(new WorldRoomMessage(second, ChatRole.CHARACTER, "A scene", LocalDateTime.now()));
        var variant = variants.save(new WorldRoomMessageVariant(message, 1, "A scene", LocalDateTime.now()));
        service.saveCastSnapshot(variant, before, service.captureCastState(second.getId()));
        service.restoreCastBeforeVariant(second.getId(), variant);
        assertThat(service.activeCast(second.getId())).extracting(s -> s.getCharacter().getName()).containsExactly("Marbas");
        service.restoreCastAfterVariant(second.getId(), variant);
        assertThat(service.activeCast(second.getId())).extracting(s -> s.getCharacter().getName()).containsExactly("Yumi");
        assertRoster(second, "Marbas", "Yumi");
    }

    @Test
    void emptyCreationRosterStaysEmptyEvenAfterWorldAndRoomCharactersAreAdded() {
        var f = fixture();
        var empty = start(f);
        var yumi = characters.save(new Character("Yumi", f.member()));
        yumi.assignWorld(f.world());
        roomCasts.save(new WorldConversationCast(f.room(), yumi, LocalDateTime.now()));
        assertRoster(empty);
        assertThat(service.applyCastChange(empty.getId(), enter(yumi))).isEqualTo(WorldRoomCastChange.none());
        assertRoster(start(f), "Yumi");
    }

    @Test
    void legacyPocketKeepsHistoricalRoomRosterWhenWorldMembershipChanges() {
        var f = fixture();
        var marbas = characters.save(new Character("Marbas", f.member()));
        roomCasts.save(new WorldConversationCast(f.room(), marbas, LocalDateTime.now()));
        var legacy = sessions.save(new WorldRoomSession(f.room(), f.member(), null, f.intro(), LocalDateTime.now()));
        var yumi = characters.save(new Character("Yumi", f.member()));
        yumi.assignWorld(f.world());
        assertRoster(legacy, "Marbas");
        assertRoster(start(f), "Yumi");
    }

    private void assertRoster(WorldRoomSession session, String... names) {
        assertThat(service.registeredCharacters(session.getId())).extracting(Character::getName).containsExactly(names);
        assertThat(context.buildSession(session.getId()).roomCast()).extracting(c -> c.characterName()).containsExactly(names);
    }

    private WorldRoomCastChange enter(Character character) {
        return new WorldRoomCastChange(List.of(character.getId()), List.of());
    }

    private WorldRoomSession start(Fixture f) {
        return service.start(f.room().getId(), f.member().getLoginId(), null, f.intro().getId());
    }

    private Fixture fixture() {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("password")).thenReturn("encoded");
        var member = members.save(Member.createLocal("roster-user", "password", "Roster", "roster@example.test", encoder));
        var world = worlds.save(new World(member, "TestWorld", "A world"));
        var room = rooms.save(new WorldConversation(world, member, "Intro room", WorldConversation.Visibility.PRIVATE, LocalDateTime.now()));
        var intro = intros.save(new WorldRoomIntro(room, "Free start", "Start freely", 0));
        return new Fixture(member, world, room, intro);
    }

    private record Fixture(Member member, World world, WorldConversation room, WorldRoomIntro intro) {}
}
