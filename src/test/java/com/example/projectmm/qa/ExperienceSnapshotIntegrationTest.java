package com.example.projectmm.qa;
import com.example.projectmm.application.qa.*;
import com.example.projectmm.application.chat.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.player.PlayerProfile;
import com.example.projectmm.domain.member.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.datasource.url=jdbc:h2:mem:pocket-qa-experience-source;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","gemini.api-key=","openai.api-key="},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import(QaEnvironment.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ExperienceSnapshotIntegrationTest {
    @Autowired EntityManager em;@Autowired TransactionTemplate tx;@Autowired WorldRoomSessionService sessions;@Autowired JdbcTemplate jdbc;
    record Fixture(long session,long owner,String login) {}
    Fixture fixture() {
        return tx.execute(status->{
            PasswordEncoder encoder=new PasswordEncoder(){public String encode(CharSequence value){return "private-password-hash";}public boolean matches(CharSequence a,String b){return false;}};
            String login="exp-owner"+UUID.randomUUID().toString().substring(0,6);
            var owner=Member.createLocal(login,"secret-password","Secret Real Name",login+"@private.example",encoder);em.persist(owner);
            var world=new World(owner,"셜록 홈즈의 런던","빅토리아 시대 런던의 탐정 이야기");world.updatePlayGuide("원전 설정을 존중하고 추리와 인물의 개성을 유지한다.");em.persist(world);
            var holmes=new Character("셜록 홈즈",owner);holmes.assignWorld(world);em.persist(holmes);
            var watson=new Character("존 왓슨",owner);watson.assignWorld(world);em.persist(watson);
            em.persist(new CharacterProfile(holmes,"관찰력이 뛰어난 탐정",null,"셜록: 단서가 있군.","예리하고 냉철함","정확한 한국어","베이커가 221B",null));
            em.persist(new CharacterProfile(watson,"의사이자 셜록의 동료",null,null,"용감하고 따뜻함","온화한 말투","군의관 출신",null));
            var player=new PlayerProfile((int)(Math.random()*1000000),"아이린","침착하고 대담한 정보상. 셜록과 신중한 신뢰 관계.",null);em.persist(player);
            var at=LocalDateTime.of(1889,4,3,18,30);var room=new WorldConversation(world,owner,"베이커가 사건",WorldConversation.Visibility.PRIVATE,at);em.persist(room);
            var intro=new WorldRoomIntro(room,"잃어버린 편지","아이린이 셜록과 왓슨을 만나 봉인된 편지를 살펴본다.",0,at,"베이커가 221B",List.of("침착함"),List.of("봉인된 편지"));em.persist(intro);em.flush();
            var session=sessions.start(room.getId(),login,player.getId(),intro.getId());session.updatePocketSettings("플레이어는 아이린이며 셜록과 동료 관계","서술과 대사를 상황에 맞게 자연스럽게 전개한다.",true,"OPENAI","gpt-5.6-sol");
            em.createQuery("select c from WorldRoomSessionCast c where c.session.id=:id",WorldRoomSessionCast.class).setParameter("id",session.getId()).getResultList().forEach(c->c.enter(at));
            var user=new WorldRoomMessage(session,ChatRole.USER,"*편지를 조심스럽게 펼친다.* 이 붉은 봉인을 보세요. 의뢰인이 아홉 시에 온다고 했어요.",at);em.persist(user);
            em.persist(new WorldRoomMessage(session,ChatRole.CHARACTER,"셜록은 붉은 봉인을 가리켰다. “왓슨, 이 문양을 보게.”",at.plusSeconds(20)));
            var memory=com.example.projectmm.domain.memory.Memory.fromWorldRoomUserSpeech(session,user,holmes,"의뢰인이 아홉 시에 온다",8,at,"experience-fixture-fingerprint",null);
            em.persist(memory);em.flush();return new Fixture(session.getId(),owner.getId(),login);
        });
    }
    @Test void copiesFullPocketAndMemoryWithoutReadingCredentialsOrChangingSource() throws Exception {
        var fixture=fixture();var exporter=new PocketExperienceSnapshotService(sessions,jdbc,new MockEnvironment()
                .withProperty("openai.api-key","configured-not-exported")
                .withProperty("openai.memory-model","gpt-5.6-sol")
                .withProperty("openai.time-model","gpt-5.6-sol")
                .withProperty("openai.compression-model","gpt-5.6-sol"));
        var before=PocketExperienceSnapshot.digest(PocketExperienceSnapshot.readTables(jdbc,fixture.session()));
        var snapshot=tx.execute(status->exporter.capture(fixture.session(),fixture.login()));
        assertThat(snapshot.extractorModel()).isEqualTo("gpt-5.6-luna");
        assertThat(snapshot.timeModel()).isEqualTo("gpt-5.6-luna");
        assertThat(snapshot.compressorModel()).isEqualTo("gpt-5.6-luna");
        String json=new ObjectMapper().writeValueAsString(snapshot);
        assertThat(json).contains("셜록 홈즈","존 왓슨","아이린","붉은 봉인","의뢰인이 아홉 시에 온다").doesNotContain("private-password-hash","private.example","configured-not-exported","Secret Real Name");
        assertThat(snapshot.digest()).isEqualTo(before);
        assertThatThrownBy(()->exporter.capture(fixture.session(),"another-owner")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var clone=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:pocket-qa-experience-clone;MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        for(String ddl:jdbc.query("SCRIPT NODATA",(rs,n)->rs.getString(1)))clone.execute(ddl);
        snapshot.restore(clone);
        assertThat(clone.queryForObject("SELECT COUNT(*) FROM world_room_sessions",Long.class)).isEqualTo(1);
        assertThat(clone.queryForList("SELECT name FROM characters",String.class)).containsExactlyInAnyOrder("셜록 홈즈","존 왓슨");
        assertThat(clone.queryForObject("SELECT name FROM player_profiles",String.class)).isEqualTo("아이린");
        assertThat(clone.queryForObject("SELECT COUNT(*) FROM world_room_messages",Long.class)).isEqualTo(2);
        assertThat(clone.queryForObject("SELECT COUNT(*) FROM memories",Long.class)).isEqualTo(1);
        assertThat(PocketExperienceSnapshot.digest(PocketExperienceSnapshot.readTables(jdbc,fixture.session()))).isEqualTo(before);
        assertThatThrownBy(()->snapshot.restore(clone)).hasMessageContaining("fresh empty");
        Path artifacts=Path.of("target/experience-fixture");Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("snapshot.json"),json);
        Files.writeString(artifacts.resolve("manifest.json"),new ObjectMapper().writeValueAsString(Map.of("maximumOutputTokens",Map.of(),"codeHashes",PocketExperienceCode.hashes(),"playerPromptVersion",ExperiencePersonaClient.VERSION)));
        Files.writeString(artifacts.resolve("config.json"),new ObjectMapper().writeValueAsString(QaSpec.mock("exp-mock-smoke",3)));
    }
}

