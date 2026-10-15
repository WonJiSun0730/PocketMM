package com.example.projectmm.qa;

import com.example.projectmm.application.chat.*;
import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.datasource.url=jdbc:h2:mem:pocket-qa-integration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","gemini.api-key=","openai.api-key="},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import(QaEnvironment.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class QaPipelineIntegrationTest {
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired WorldRoomSessionService sessions;
    @Autowired WorldRoomDialogueService dialogue;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomMessageVariantRepository variants;
    @Autowired PocketMemoryInspectorService inspector;
    @Autowired AiUsageTracker usage;
    @Autowired JdbcTemplate jdbc;
    @Autowired DialogueGateway gateway;
    @TempDir Path temporary;

    @Test void smokeAndHundredTurnsUseActualCommitExtractionPersistenceRecallAndExport() throws Exception {
        for(int turns:List.of(20,100)) {
            var spec=QaSpec.mock("pipeline-"+turns,turns);
            var fixture=QaEnvironment.fixture(spec.runId(),spec,em,tx,sessions);
            var target=QaEnvironment.pipeline(spec,fixture.sessionId(),dialogue,messages,variants,inspector,usage,jdbc,tx);
            var runner=new QaRunner(temporary.resolve(spec.runId()),QaEnvironment.mockPersona(),target);
            runner.create(spec,QaScenarios.standard(turns));
            for(int i=0;i<turns;i++)runner.step();
            var result=runner.read();assertThat(result.status).withFailMessage("Run failed: %s",result.reason).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("select count(*) from world_room_messages where session_id=?",Long.class,fixture.sessionId())).isEqualTo(2L*turns);
            assertThat(inspector.snapshotAll(fixture.sessionId()).stream().flatMap(x->x.memories().stream()).map(PocketMemoryInspectorService.MemoryItem::evidenceQuote))
                    .anyMatch(s->s.contains("B731"));
            assertThat(result.turns.get(1).outcome().memoryAfterJson()).contains("B731");
            assertThat(result.turns.get(15).outcome().evaluationJson()).contains("recalledMemories","서문");
            assertThat(result.calls).extracting(QaRunner.Usage::role).contains("TARGET","AUXILIARY");
            var again=target.send(spec.runId()+":1",result.turns.getFirst().input());
            assertThat(again.reply()).isEqualTo(result.turns.getFirst().outcome().reply());
            assertThatThrownBy(()->target.send(spec.runId()+":1","different payload")).hasMessageContaining("different input");
            assertThat(jdbc.queryForObject("select count(*) from world_room_messages where session_id=?",Long.class,fixture.sessionId())).isEqualTo(2L*turns);
            QaReports.write(Path.of("target/qa-evidence/"+spec.runId()),result);
        }
    }
    @Test void newSyntheticSessionCannotRecallAnotherSessionsFacts() {
        var spec=QaSpec.mock("isolation",20);
        var a=QaEnvironment.fixture("isolation-a",spec,em,tx,sessions);
        var b=QaEnvironment.fixture("isolation-b",spec,em,tx,sessions);
        var first=QaEnvironment.pipeline(spec,a.sessionId(),dialogue,messages,variants,inspector,usage,jdbc,tx);
        first.send("isolation-a:1","내가 준 은색 나침반에는 PRIVATE_CANARY가 새겨져 있어.");
        var second=QaEnvironment.pipeline(spec,b.sessionId(),dialogue,messages,variants,inspector,usage,jdbc,tx);
        var reply=second.send("isolation-b:1","은색 나침반의 각인이 뭐였지?");
        assertThat(reply.evaluationJson()).doesNotContain("PRIVATE_CANARY");assertThat(reply.memories()).isEmpty();
    }
}
