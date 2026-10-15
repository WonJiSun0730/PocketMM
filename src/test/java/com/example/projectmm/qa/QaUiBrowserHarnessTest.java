package com.example.projectmm.qa;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.domain.member.*;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.character.Character;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.List;

/** Explicit browser smoke harness. Contains synthetic accounts only and disables all paid keys. */
@EnabledIfSystemProperty(named="pocket.qa.ui.preview",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={
        "spring.profiles.active=qa-ui-preview","server.address=127.0.0.1","server.port=18082",
        "server.servlet.session.cookie.name=POCKET_QA_UI_PREVIEW","app.private-test.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:pocket-qa-ui-browser;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","gemini.api-key=","openai.api-key=","app.qa-ui.api-key=",
        "app.qa-ui.enabled=true","app.qa-ui.workspace=.","app.qa-ui.prices-file=tools/qa-ui-prices.example.json",
        "management.health.mail.enabled=false","management.health.redis.enabled=false","pocket.background.enabled=false"})
class QaUiBrowserHarnessTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PasswordEncoder encoder;
    @Autowired WorldRoomSessionService sessions;
    @Test void serveSyntheticDeveloperPocketForBrowserSmoke() throws Exception {
        var tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{
            var owner=Member.createLocal("qa-ui-dev","qa-ui-only","QA 개발자","qa-ui@example.invalid",encoder);owner.changeRole(Role.DEVELOPER);em.persist(owner);
            var ordinary=Member.createLocal("qa-ui-user","qa-ui-only","QA 일반계정","qa-ui-user@example.invalid",encoder);em.persist(ordinary);
            var world=new World(owner,"QA 전용 세계","합성 데이터로만 UI 검증");em.persist(world);
            var character=new Character("QA 동료",owner);character.assignWorld(world);em.persist(character);
            var at=LocalDateTime.of(2026,1,1,12,0);
            var room=new WorldConversation(world,owner,"자동 테스트 UI 검증",WorldConversation.Visibility.PRIVATE,at);em.persist(room);
            var intro=new WorldRoomIntro(room,"QA 시작","광장에서 동료와 대화를 시작한다.",0,at,"마을 광장",List.of("편안함"),List.of("가방"));em.persist(intro);
            var session=sessions.start(room.getId(),owner.getLoginId(),null,intro.getId());
            em.createQuery("select c from WorldRoomSessionCast c where c.session.id=:id",WorldRoomSessionCast.class).setParameter("id",session.getId()).getResultList().forEach(c->c.enter(at));
        });
        Path stop=Path.of("target/qa-ui-preview.stop");Files.deleteIfExists(stop);
        Files.writeString(Path.of("target/qa-ui-preview.ready"),"Ready: synthetic developer and user; paid API disabled");
        long deadline=System.nanoTime()+Duration.ofMinutes(20).toNanos();
        while(!Files.exists(stop)&&System.nanoTime()<deadline)Thread.sleep(1000);
    }
}
