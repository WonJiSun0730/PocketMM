package com.example.projectmm.qa;

import com.example.projectmm.application.chat.*;
import com.example.projectmm.application.dialogue.DialogueGateway;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import jakarta.persistence.EntityManager;
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

/** Explicitly launched CLI harness. Ordinary test runs cannot opt into paid APIs. */
@EnabledIfSystemProperty(named="pocket.qa.execute",matches="true")
@DataJpaTest(showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers=QaDatabaseInitializer.class)
@Import(QaEnvironment.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PersonaQaExecutionTest {
    @Autowired QaSpec spec;
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
    @Autowired QaSpendGuard guard;

    static void requireSuccessfulExecution(QaRunner.State state) {
        if(java.util.Set.of("REVIEW_REQUIRED","FAILED","BUDGET_STOP").contains(state.status))
            throw new AssertionError("QA status="+state.status+" stage="+state.stage+"; review run.json diagnostics. Automatic retry prohibited.");
    }

    @Test void executeApprovedIsolatedRun() throws Exception {
        if(!spec.runId().equals(System.getProperty("pocket.qa.run-id")))throw new IllegalArgumentException("Run ID mismatch");
        if(spec.paid()&&!spec.runId().equals(System.getProperty("pocket.qa.approve")))
            throw new IllegalArgumentException("Paid invocation requires explicit pocket.qa.approve matching this run ID");
        jdbc.execute("CREATE TABLE IF NOT EXISTS qa_runs(run_id VARCHAR(60) PRIMARY KEY, session_id BIGINT NOT NULL, spec_json LONGTEXT NOT NULL)");
        var mapper=new ObjectMapper();var json=mapper.writeValueAsString(spec);
        var rows=jdbc.queryForList("SELECT session_id,spec_json FROM qa_runs WHERE run_id=?",spec.runId());
        long id;
        if(rows.isEmpty()) {
            id=tx.execute(status->{
                var fixture=QaEnvironment.fixture(spec.runId(),spec,em,tx,sessions);
                jdbc.update("INSERT INTO qa_runs(run_id,session_id,spec_json) VALUES(?,?,?)",spec.runId(),fixture.sessionId(),json);
                return fixture.sessionId();
            });
        } else {
            if(!mapper.readValue(rows.getFirst().get("SPEC_JSON").toString(),QaSpec.class).equals(spec))
                throw new IllegalArgumentException("Saved config differs; use a new run ID rather than altering a resumed experiment");
            id=((Number)rows.getFirst().get("SESSION_ID")).longValue();
        }
        var path=Path.of("target/pocket-qa",spec.runId());
        var target=QaEnvironment.pipeline(spec,id,dialogue,messages,variants,inspector,usage,jdbc,tx);
        var persona=spec.paid()?new QaPersonaClient(spec,QaEnvironment.key(),QaEnvironment.qaClient(guard)):QaEnvironment.mockPersona();
        var runner=new QaRunner(path,persona,target);
        if(!Files.exists(path.resolve("run.json"))) {
            String scenarioPath=System.getProperty("pocket.qa.scenario");
            var scenario=scenarioPath==null?QaScenarios.standard(spec.turns()):new QaScenarios(java.util.Arrays.asList(
                    mapper.readValue(Files.readString(Path.of(scenarioPath)),QaScenarios.Event[].class)));
            runner.create(spec,scenario);
        }
        if(!runner.read().spec.equals(spec))throw new IllegalArgumentException("Checkpoint config mismatch");
        if(Boolean.getBoolean("pocket.qa.recover")) {var recovered=runner.recoverCommitted();QaReports.write(path,recovered);requireSuccessfulExecution(recovered);return;}
        if(System.getProperty("pocket.qa.reconcile")!=null) {
            var reconciliation=mapper.readTree(Files.readString(Path.of(System.getProperty("pocket.qa.reconcile"))));
            var costs=new java.util.HashMap<String,java.math.BigDecimal>();
            reconciliation.path("costs").properties().forEach(e->costs.put(e.getKey(),new java.math.BigDecimal(e.getValue().asText())));
            runner.authorizeRetry(costs,reconciliation.path("evidence").asText());
        }
        guard.begin(runner.read());
        int steps=Integer.getInteger("pocket.qa.steps",spec.turns());
        if(steps<1)throw new IllegalArgumentException("steps must be positive");
        for(int i=0;i<steps;i++) {
            var state=runner.step();
            if(spec.turns()<=100||state.turns.size()%10==0||!state.status.equals("RUNNING"))QaReports.write(path,state);
            System.out.printf("QA %s %d/%d %s knownUSD=%s unknown=%s%n",spec.runId(),state.turns.size(),spec.turns(),state.status,state.knownUsd(),state.unknownCost());
            if(!state.status.equals("RUNNING"))break;
        }
        var finalState=runner.read();
        QaReports.write(path,finalState);
        requireSuccessfulExecution(finalState);
    }
}
