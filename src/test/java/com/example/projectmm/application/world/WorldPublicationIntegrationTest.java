package com.example.projectmm.application.world;

import com.example.projectmm.application.chat.*;
import com.example.projectmm.domain.world.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.member.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({WorldPublicationService.class, WorldCoverService.class, WorldRoomSessionService.class, WorldRoomService.class})
class WorldPublicationIntegrationTest {
    @Autowired WorldPublicationService publication;
    @Autowired WorldCoverService covers;
    @Autowired WorldRoomService roomService;
    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRepository worlds;
    @Autowired MemberRepository members;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomSessionRepository sessions;
    @Autowired jakarta.persistence.EntityManager em;
    @TempDir Path directory;
    Member owner, reader;
    World world;
    WorldConversation room;
    WorldRoomIntro intro;

    @BeforeEach void setup() {
        var encoder = mock(PasswordEncoder.class); when(encoder.encode(anyString())).thenReturn("encoded");
        owner = members.save(Member.createLocal("publisher", "password", "배포자", "publisher@example.test", encoder));
        reader = members.save(Member.createLocal("reader", "password", "이용자", "reader@example.test", encoder));
        world = worlds.save(new World(owner, "안개 도시", "제작자 비공개 메모"));
        world.updatePlayGuide("비 오는 도시의 탐정이 되어 주세요.");
        room = rooms.save(new WorldConversation(world, owner, "시작", WorldConversation.Visibility.PRIVATE, LocalDateTime.now()));
        intro = intros.save(new WorldRoomIntro(room, "첫 의뢰", "*문을 두드리는 소리가 들렸다.*", 0));
        ReflectionTestUtils.setField(covers, "directory", directory.toString());
    }
    void publish() { world.updateCoverImageUrl("/cover.png"); publication.publish(world.getId(), owner.getLoginId()); }

    @Test void unpublishedWorldsStayHiddenAndOnlyOwnerCanPublishOrChangeTags() {
        assertThat(publication.list(0).items()).isEmpty();
        assertThatThrownBy(() -> publication.detail(world.getId())).hasMessageContaining("404");
        assertThatThrownBy(() -> publication.publish(world.getId(), reader.getLoginId())).hasMessageContaining("404");
        assertThatThrownBy(() -> publication.update(world.getId(), reader.getLoginId(), List.of("금지"))).hasMessageContaining("404");
        assertThatThrownBy(() -> publication.publish(world.getId(), owner.getLoginId())).hasMessageContaining("표지");
        world.updateCoverImageUrl("/cover.png"); intros.delete(intro); intros.flush();
        assertThatThrownBy(() -> publication.publish(world.getId(), owner.getLoginId())).hasMessageContaining("인트로");
    }

    @Test void tagsPersistNormalizeAndRejectOversizedOrInvalidInputs() {
        publication.update(world.getId(), owner.getLoginId(), List.of("#판타지", "미스터리", "판타지"));
        em.flush(); em.clear();
        assertThat(publication.settings(world.getId(), owner.getLoginId()).hashtags()).containsExactly("판타지", "미스터리");
        assertThatThrownBy(() -> publication.update(world.getId(), owner.getLoginId(), List.of("a".repeat(31)))).hasMessageContaining("30자");
        assertThatThrownBy(() -> publication.update(world.getId(), owner.getLoginId(), List.of("<script>"))).hasMessageContaining("30자");
        assertThatThrownBy(() -> publication.update(world.getId(), owner.getLoginId(), java.util.Collections.nCopies(11, "tag"))).hasMessageContaining("10개");
    }

    @Test void readerStartsNewOwnedPocketsWithoutEditingOriginalAndCanContinueAfterUnpublish() {
        publish();
        var detail = publication.detail(world.getId());
        assertThat(detail.world().publisher()).isEqualTo("배포자");
        assertThat(detail.playGuide()).isEqualTo(world.getPlayGuide());
        assertThat(detail.intros()).extracting(WorldPublicationService.Intro::content).containsExactly(intro.getContent());
        var first = publication.start(world.getId(), reader.getLoginId(), intro.getId(), null);
        var second = publication.start(world.getId(), reader.getLoginId(), intro.getId(), null);
        assertThat(first.session().id()).isNotEqualTo(second.session().id());
        assertThat(sessionService.pocket(first.session().id(), reader.getLoginId()).getMember().getId()).isEqualTo(reader.getId());
        assertThatThrownBy(() -> sessionService.pocket(first.session().id(), owner.getLoginId())).hasMessageContaining("404");
        assertThatThrownBy(() -> roomService.updateIntro(room.getId(), intro.getId(), reader.getLoginId(), "변경", "변경", 0, null, null, List.of(), List.of())).hasMessageContaining("403");
        assertThat(intro.getTitle()).isEqualTo("첫 의뢰");
        publication.unpublish(world.getId(), owner.getLoginId());
        assertThat(publication.list(0).items()).isEmpty();
        assertThatThrownBy(() -> publication.start(world.getId(), reader.getLoginId(), intro.getId(), null)).hasMessageContaining("404");
        assertThat(sessionService.ownedSession(room.getId(), first.session().id(), reader.getLoginId()).getId()).isEqualTo(first.session().id());
    }

    @Test void foreignIntroCannotBeUsedAndOrdinaryPrivateStartRemainsBlocked() {
        publish();
        var otherWorld = worlds.save(new World(owner, "다른 월드", null));
        var otherRoom = rooms.save(new WorldConversation(otherWorld, owner, "다른 시작", WorldConversation.Visibility.PRIVATE, LocalDateTime.now()));
        var foreign = intros.save(new WorldRoomIntro(otherRoom, "다른 인트로", "다른 내용", 0));
        assertThatThrownBy(() -> publication.start(world.getId(), reader.getLoginId(), foreign.getId(), null)).hasMessageContaining("404");
        assertThatThrownBy(() -> sessionService.startPublished(world.getId(), otherRoom.getId(), reader.getLoginId(), null, foreign.getId())).hasMessageContaining("404");
        assertThatThrownBy(() -> sessionService.start(room.getId(), reader.getLoginId(), null, intro.getId())).hasMessageContaining("404");
    }

    @Test void turnCountAggregatesAcrossPlayersWithoutCountingInputsOrAbsenceReports() {
        publish();
        var first = publication.start(world.getId(), reader.getLoginId(), intro.getId(), null);
        var second = publication.start(world.getId(), owner.getLoginId(), intro.getId(), null);
        var one = sessions.findById(first.session().id()).orElseThrow();
        var two = sessions.findById(second.session().id()).orElseThrow();
        messages.save(new WorldRoomMessage(one, ChatRole.USER, "입력", LocalDateTime.now()));
        messages.save(new WorldRoomMessage(one, ChatRole.CHARACTER, "응답1", LocalDateTime.now()));
        messages.save(new WorldRoomMessage(two, ChatRole.CHARACTER, "응답2", LocalDateTime.now()));
        var absence = new WorldRoomMessage(one, ChatRole.CHARACTER, "부재 활동", LocalDateTime.now()); absence.markAbsenceReport(42L); messages.save(absence);
        assertThat(publication.list(0).items().getFirst().totalTurns()).isEqualTo(2);
        assertThat(publication.detail(world.getId()).world().totalTurns()).isEqualTo(2);
    }

    @Test void validCoverIsStoredAndPrivateCoverRejectsOtherReadersAndTraversal() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 3, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        var url = covers.upload(world.getId(), owner.getLoginId(), new MockMultipartFile("cover", "image.png", "image/png", bytes.toByteArray()));
        var filename = url.substring(url.lastIndexOf('/') + 1);
        assertThat(covers.image(world.getId(), owner.getLoginId(), filename)).exists();
        assertThatThrownBy(() -> covers.image(world.getId(), reader.getLoginId(), filename)).hasMessageContaining("404");
        publication.publish(world.getId(), owner.getLoginId());
        assertThat(covers.image(world.getId(), reader.getLoginId(), filename)).exists();
        assertThatThrownBy(() -> covers.image(world.getId(), owner.getLoginId(), "../secret.png")).hasMessageContaining("404");
        assertThatThrownBy(() -> covers.upload(world.getId(), reader.getLoginId(), new MockMultipartFile("cover", "x.png", "image/png", bytes.toByteArray()))).hasMessageContaining("404");
    }

    @Test void invalidAndOversizedCoversAreRejectedWithoutChangingWorld() {
        assertThatThrownBy(() -> covers.upload(world.getId(), owner.getLoginId(), new MockMultipartFile("cover", "x.svg", "image/png", "<svg/>".getBytes()))).hasMessageContaining("PNG/JPEG");
        assertThatThrownBy(() -> covers.upload(world.getId(), owner.getLoginId(), new MockMultipartFile("cover", new byte[3 * 1024 * 1024 + 1]))).hasMessageContaining("3MB");
        assertThat(world.getCoverImageUrl()).isNull();
    }
}
