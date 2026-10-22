package com.example.projectmm.qa;

import com.example.projectmm.application.chat.*;
import com.example.projectmm.application.dialogue.WorldRoomContextBuilder;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.application.qa.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

@EnabledIfSystemProperty(named="pocket.experience.execute",matches="true")
@DataJpaTest(showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers=ExperienceDatabaseInitializer.class)
@Import(ExperienceEnvironment.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ExperienceExecutionTest {
    @Autowired QaSpec spec;@Autowired JdbcTemplate jdbc;@Autowired TransactionTemplate tx;
    @Autowired WorldRoomContextBuilder contexts;@Autowired WorldRoomDialogueService dialogue;
    @Autowired WorldRoomMessageRepository messages;@Autowired WorldRoomMessageVariantRepository variants;
    @Autowired PocketMemoryInspectorService inspector;@Autowired AiUsageTracker usage;@Autowired ExperienceTransport transport;
    static Path directory(){return Path.of("target/pocket-qa-experience",System.getProperty("pocket.qa.run-id","invalid"));}
    static PocketExperienceSnapshot snapshot() throws java.io.IOException {return new ObjectMapper().readValue(Files.readString(directory().resolve("snapshot.json")),PocketExperienceSnapshot.class);}
    @Test void executeCopiedPocketOnly() throws Exception {
        if(!spec.runId().equals(System.getProperty("pocket.qa.run-id")))throw new IllegalStateException("Run ID mismatch");
        if(spec.paid()&&!spec.runId().equals(System.getProperty("pocket.qa.approve")))throw new IllegalStateException("Explicit paid approval required");
        var snapshot=snapshot();var path=directory();var mapper=new ObjectMapper();
        var expected=mapper.readTree(Files.readString(path.resolve("manifest.json"))).path("codeHashes");
        if(!mapper.valueToTree(PocketExperienceCode.hashes()).equals(expected))throw new IllegalStateException("Running server and worker implementations differ; rebuild and restart the server");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM world_room_sessions",Long.class)==0) {
            snapshot.restore(jdbc);
            Files.writeString(path.resolve("snapshot-verification.json"),mapper.writeValueAsString(Map.of("verified",true,"digest",snapshot.digest(),"at",java.time.Instant.now().toString())));
            // Sole service-setting difference: the user-selected target provider/model.
            jdbc.update("UPDATE world_room_sessions SET ai_provider='OPENAI',ai_model=? WHERE id=?",spec.targetModel(),snapshot.sessionId());
        }
        if(!Files.exists(path.resolve("snapshot-verification.json")))throw new IllegalStateException("Clone was not verified");
        var pipeline=new QaPipeline(spec,snapshot.sessionId(),dialogue,messages,variants,inspector,usage,jdbc,tx);
        QaRunner.Target target=new QaRunner.Target() {
            public QaRunner.Outcome send(String id,String input) {
                var before=transport.ids();var outcome=pipeline.send(id,input);
                return spec.paid()?transport.outcomeSince(before,outcome):outcome;
            }
            public Optional<QaRunner.Outcome> recover(String id) {
                return pipeline.recover(id).map(o->new QaRunner.Outcome(o.reply(),o.memories(),o.evaluationJson(),o.diagnosticsJson(),o.memoryBeforeJson(),o.memoryAfterJson(),
                        List.of(new QaRunner.Usage("TARGET",spec.targetModel(),"RECOVERED_COMMIT",0,0,null,0,"RECONCILE_LEDGER",null)),o.error()));
            }
        };
        var runner=new QaRunner(path,new ExperiencePersonaClient(spec,snapshot.sessionId(),contexts,tx,transport,jdbc),target,0);
        if(!Files.exists(path.resolve("run.json")))runner.create(spec,new QaScenarios(List.of()));
        if(!runner.read().spec.equals(spec))throw new IllegalStateException("Config differs; use a new run");
        transport.begin(runner.read());
        for(int i=0;i<spec.turns();i++) {
            var state=runner.step();
            if(transport.budgetBlocked&&!state.unknownCost()) {
                state.status="BUDGET_STOP";if(state.reason==null)state.reason="하드캡 정책에 따라 다음 호출 전 중단했습니다. 예산 상세의 입력 계수·출력 상한·최대 예약액·잔액·중단 사유를 확인하세요.";
                var temp=Files.createTempFile(path,"experience-state-",".tmp");Files.writeString(temp,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
                try { QaAtomicFiles.replace(temp,path.resolve("run.json")); }
                finally { Files.deleteIfExists(temp); }
            }
            QaReports.write(path,state);
            var events=Files.exists(path.resolve("ledger.jsonl"))?Files.readAllLines(path.resolve("ledger.jsonl")).stream().filter(line->!line.isBlank()).map(mapper::readTree).toList():List.<tools.jackson.databind.JsonNode>of();
            var analysis=PocketExperienceAnalysis.fullAnalysis(mapper.valueToTree(state),mapper.readTree(Files.readString(path.resolve("manifest.json"))),events);
            Files.writeString(path.resolve("analysis.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(analysis));
            Files.writeString(path.resolve("experience-report.md"),PocketExperienceAnalysis.markdown(mapper.valueToTree(state),snapshot)+"\n## 테스트 전용 하드캡 정책 및 실제 서비스와의 차이\n\n```json\n"+Files.readString(path.resolve("manifest.json"))+"\n```\n\n## 예약·정산 상세\n\n```json\n"+(Files.exists(path.resolve("budget.json"))?Files.readString(path.resolve("budget.json")):"{}")+"\n```\n");
            System.out.printf("EXPERIENCE %s %d/%d %s%n",spec.runId(),state.turns.size(),spec.turns(),state.status);
            if(!state.status.equals("RUNNING"))break;
        }
        PersonaQaExecutionTest.requireSuccessfulExecution(runner.read());
    }
}
