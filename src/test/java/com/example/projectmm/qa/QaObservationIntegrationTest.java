package com.example.projectmm.qa;

import com.example.projectmm.application.chat.*;
import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.datasource.url=jdbc:h2:mem:pocket-qa-observation;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","gemini.api-key=","openai.api-key="},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import(QaEnvironment.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class QaObservationIntegrationTest {
    @Autowired EntityManager em;@Autowired TransactionTemplate tx;@Autowired WorldRoomSessionService sessions;
    @Autowired WorldRoomDialogueService dialogue;@Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomMessageVariantRepository variants;@Autowired PocketMemoryInspectorService inspector;
    @Autowired AiUsageTracker usage;@Autowired JdbcTemplate jdbc;@Autowired SceneContinuityService continuity;
    @TempDir Path dir;

    @Test void hundredTurnsKeepOriginalsAndTrustedFactsDespiteEightyReviewErrorsAndRecoverEvidence() throws Exception {
        var spec=QaSpec.mock("observe-100",100);
        var fixture=QaEnvironment.fixture(spec.runId(),spec,em,tx,sessions);
        var gateway=mock(DialogueGateway.class);var generated=new AtomicInteger();var reviews=new AtomicInteger();
        var json=new ObjectMapper();
        var originalGateway=ReflectionTestUtils.getField(continuity,"gateway");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenAnswer(call->{
            usage.record(fixture.sessionId(),"MOCK",spec.targetModel(),0,0,0L,0,0);
            return new WorldRoomTurn("Original target reply "+generated.incrementAndGet(),WorldRoomCastChange.none());
        });
        when(gateway.reviewWorldRoomContinuity(any(),anyString())).thenAnswer(call->{
            int n=reviews.incrementAndGet();
            usage.record(fixture.sessionId(),"MOCK","mock-review",0,0,0L,0,0);
            var poison=new SceneFacts.Change("poison",SceneFacts.Kind.PERSON,"REVIEWER_POISON",
                    SceneFacts.Certainty.OBSERVED,SceneFacts.Source.REPLY,"REVIEWER_POISON",null,SceneFacts.Reason.NEW);
            if(n%5==2)return "not review JSON";
            if(n%5==1)return json.writeValueAsString(new SceneContinuityService.Review(false,
                    new WorldRoomTurn("REVIEWER_POISON",WorldRoomCastChange.none()),List.of(poison),List.of("unresolved conflict")));
            if(n%5==3)return json.writeValueAsString(new SceneContinuityService.Review(true,null,List.of(poison),List.of()));
            if(n%5==4)return json.writeValueAsString(new SceneContinuityService.Review(true,
                    new WorldRoomTurn("REVIEWER_POISON",WorldRoomCastChange.none()),List.of(poison),List.of()));
            var fact=new SceneFacts.Change("trusted",SceneFacts.Kind.PERSON,"original narrator",
                    SceneFacts.Certainty.OBSERVED,SceneFacts.Source.REPLY,"Original target reply",null,SceneFacts.Reason.NEW);
            return json.writeValueAsString(new SceneContinuityService.Review(true,null,n==5?List.of(fact):List.of(),List.of()));
        });
        ReflectionTestUtils.setField(continuity,"gateway",gateway);
        try {
            var pipeline=new QaPipeline(spec,fixture.sessionId(),dialogue,messages,variants,inspector,usage,jdbc,tx);
            var runner=new QaRunner(dir,QaEnvironment.mockPersona(),pipeline);
            runner.create(spec,QaScenarios.standard(100));
            for(int i=0;i<100;i++)runner.step();
            var state=runner.read();assertThat(state.status).isEqualTo("COMPLETED");
            assertThat(state.turns).hasSize(100);assertThat(generated.get()).isEqualTo(100);assertThat(reviews.get()).isEqualTo(100);
            for(var turn:state.turns)assertThat(turn.outcome().reply()).isEqualTo("Original target reply "+turn.number());
            var analysis=com.example.projectmm.application.qa.PocketExperienceAnalysis.analyze(json.valueToTree(state));
            assertThat(analysis.findings().stream().filter(f->f.type().startsWith("CONTINUITY_"))).hasSize(120);
            assertThat(analysis.findings().stream().filter(f->f.type().startsWith("CONTINUITY_")).map(f->f.turn()).distinct()).hasSize(80);
            QaReports.write(dir,state);
            assertThat(java.nio.file.Files.readString(dir.resolve("report.md"))).contains("연속성 검사 관찰","unresolved conflict");
            assertThat(java.nio.file.Files.readString(dir.resolve("turns.csv")).lines()).hasSize(101);
            var again=pipeline.send("observe-100:1",state.turns.getFirst().input());
            assertThat(again.diagnosticsJson()).contains("REJECTED","unresolved conflict");
            for(var turn:state.turns) {
                var timing=json.readTree(turn.outcome().diagnosticsJson()).path("chatTiming");
                assertThat(timing.path("source").asText()).isEqualTo("QA_SERVER");
                assertThat(timing.path("bodyReadyMs").isNumber()).isTrue();
                assertThat(timing.path("preGenerationMs").isNumber()).isTrue();
                assertThat(timing.path("bodyReadyMs").asLong()).isGreaterThanOrEqualTo(timing.path("preGenerationMs").asLong());
                for(String stage:List.of("SESSION_LOCK","INITIAL_SCENE_SETUP","TIME_INTERPRETATION","CONTEXT_BUILD","CONTEXT_MEMORY_RECALL","SCENE_FACTS_PREPARE","TARGET_GENERATION"))
                    assertThat(timing.path("stages").path(stage).path("durationMs").isNumber()).as(stage).isTrue();
                assertThat(timing.path("stages").path("STATE_SNAPSHOT").path("count").asInt()).isEqualTo(2);
                assertThat(timing.path("stages").has("TARGET_TOKEN_COUNT")).isFalse();
                assertThat(timing.path("reviewCompleteMs").asLong()).isGreaterThanOrEqualTo(timing.path("bodyReadyMs").asLong());
                assertThat(timing.path("processingCompleteMs").isNumber()).isTrue();
                assertThat(timing.path("processingCompleteMs").asLong()).isGreaterThanOrEqualTo(timing.path("reviewCompleteMs").asLong());
            }
            var timedAnalysis=com.example.projectmm.application.qa.PocketExperienceAnalysis.fullAnalysis(json.valueToTree(state),json.createObjectNode());
            assertThat(timedAnalysis.path("chatTiming").path("bodyReadyMs").path("count").asInt()).isEqualTo(100);
            assertThat(generated.get()).isEqualTo(100);
            // Simulate a crash after commit but before the JSON receipt was finalized.
            jdbc.update("UPDATE qa_turn_receipts SET result_json=NULL WHERE request_id=?","observe-100:1");
            assertThat(pipeline.recover("observe-100:1").orElseThrow().diagnosticsJson()).contains("unresolved conflict");
            var recovered=json.readTree(pipeline.recover("observe-100:1").orElseThrow().diagnosticsJson()).path("chatTiming");
            assertThat(recovered.path("processingCompleteMs").isNumber()).isTrue();
            jdbc.update("UPDATE qa_turn_receipts SET timing_json=? WHERE request_id=?",
                    "{\"source\":\"QA_SERVER\",\"bodyReadyMs\":5,\"reviewCompleteMs\":8,\"processingCompleteMs\":null}","observe-100:1");
            var partial=json.readTree(pipeline.recover("observe-100:1").orElseThrow().diagnosticsJson()).path("chatTiming");
            assertThat(partial.path("bodyReadyMs").asLong()).isEqualTo(5);
            assertThat(partial.path("processingCompleteMs").isNull()).isTrue();
            assertThat(generated.get()).isEqualTo(100);
            var facts=jdbc.queryForList("SELECT after_scene_facts_json FROM world_room_message_variants WHERE after_scene_facts_json IS NOT NULL ORDER BY id",String.class);
            assertThat(facts).noneMatch(f->f.contains("REVIEWER_POISON"));
            assertThat(facts.getLast()).contains("trusted","original narrator");
            assertThat(inspector.snapshotAll(fixture.sessionId()).toString()).doesNotContain("REVIEWER_POISON");
        } finally {ReflectionTestUtils.setField(continuity,"gateway",originalGateway);}
        assertThat(SceneContinuityObservation.enabled()).isFalse();
    }
}
