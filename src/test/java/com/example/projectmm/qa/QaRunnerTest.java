package com.example.projectmm.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class QaRunnerTest {
    @TempDir Path dir;
    static QaRunner.Outcome ok(String input) {return new QaRunner.Outcome("응답 "+input,List.of(),null,null,null,null,List.of(),null);}
    static class Target implements QaRunner.Target {
        final Map<String,QaRunner.Outcome> receipts=new HashMap<>();int sends;
        public QaRunner.Outcome send(String id,String input){sends++;return receipts.computeIfAbsent(id,key->ok(input));}
        public Optional<QaRunner.Outcome> recover(String id){return Optional.ofNullable(receipts.get(id));}
    }
    @Test void hundredTurnsBoundPersonaContextAndNeverExposeAnswerKey() throws Exception {
        var target=new Target();var count=new AtomicInteger();
        QaRunner.Persona persona=input->{count.incrementAndGet();assertThat(input.recent()).hasSizeLessThanOrEqualTo(3);
            assertThat(new ObjectMapper().writeValueAsString(input)).doesNotContain("SECRET_ORACLE");
            return new QaRunner.Generation("반응 "+input.turn(),List.of(),null);};
        var runner=new QaRunner(dir,persona,target);
        runner.create(QaSpec.mock("hundred",100),new QaScenarios(List.of(new QaScenarios.Event(3,"secret","공개 사건",QaScenarios.Metric.STORAGE,List.of("SECRET_ORACLE"),List.of(),false))));
        for(int i=0;i<100;i++)runner.step();
        assertThat(runner.read().status).isEqualTo("COMPLETED");assertThat(target.sends).isEqualTo(100);assertThat(count.get()).isEqualTo(99);
        runner.step();assertThat(target.sends).isEqualTo(100);
    }
    @Test void pauseResumeAndStopDoNotDuplicateTurns() throws Exception {
        var target=new Target();var runner=new QaRunner(dir,QaEnvironment.mockPersona(),target);
        runner.create(QaSpec.mock("pause",20),QaScenarios.standard(20));runner.step();runner.command("PAUSE");
        assertThat(runner.step().status).isEqualTo("PAUSED");assertThat(target.sends).isEqualTo(1);
        runner.command("RESUME");runner.step();assertThat(target.sends).isEqualTo(2);
        runner.command("STOP");assertThat(runner.step().status).isEqualTo("STOPPED");runner.command("RESUME");runner.step();assertThat(target.sends).isEqualTo(2);
    }
    @Test void committedReceiptRecoversCrashWithoutResend() throws Exception {
        var target=new Target();var runner=new QaRunner(dir,QaEnvironment.mockPersona(),target);
        runner.create(QaSpec.mock("recover",20),QaScenarios.standard(20));
        var state=runner.read();state.stage="TARGET_IN_FLIGHT";state.pendingInput="이미 처리한 입력";
        Files.writeString(dir.resolve("run.json"),new ObjectMapper().writeValueAsString(state));
        target.receipts.put("recover:1",ok(state.pendingInput));
        assertThat(runner.step().turns).hasSize(1);assertThat(target.sends).isZero();
    }
    @Test void uncertainFailureNeverAutomaticallyRetries() throws Exception {
        var target=new Target(){public QaRunner.Outcome send(String id,String input){sends++;throw new IllegalStateException("timeout");}};
        var runner=new QaRunner(dir,QaEnvironment.mockPersona(),target);runner.create(QaSpec.mock("failure",20),QaScenarios.standard(20));
        assertThat(runner.step().status).isEqualTo("REVIEW_REQUIRED");runner.step();assertThat(target.sends).isEqualTo(1);
        assertThatThrownBy(runner::recoverCommitted).hasMessageContaining("No committed receipt");
    }
    @Test void budgetReservesBeforeAnyCallAndUnknownUsageStops() throws Exception {
        var target=new Target();var mock=QaSpec.mock("budget",20);
        var spec=new QaSpec(mock.runId(),20,false,null,mock.persona(),mock.personaModel(),mock.targetModel(),mock.extractionModel(),
                new BigDecimal("0.1"),new BigDecimal("0.2"),Map.of(),3,true);
        var runner=new QaRunner(dir,QaEnvironment.mockPersona(),target);runner.create(spec,QaScenarios.standard(20));
        assertThat(runner.step().status).isEqualTo("BUDGET_STOP");assertThat(target.sends).isZero();
        var other=new QaRunner(dir.resolve("unknown"),i->new QaRunner.Generation("hello",List.of(new QaRunner.Usage("PERSONA","x","x",0,0,null,0,"UNKNOWN",null)),null),target);
        other.create(QaSpec.mock("unknown",20),QaScenarios.standard(20));assertThat(other.step().status).isEqualTo("REVIEW_REQUIRED");assertThat(target.sends).isZero();
    }
    @Test void costsAccountReadWriteAndLargeContextSeparately() {
        var rate=new QaSpec.Rate(new BigDecimal("2"),new BigDecimal("0.2"),new BigDecimal("2.5"),new BigDecimal("8"),100,
                new BigDecimal("2"),new BigDecimal("1.5"),"test fixture prices","2026-10-15");
        assertThat(rate.cost(100,30,20L,10)).isEqualByComparingTo("0.000236");
        assertThat(rate.cost(101,30,20L,10)).isEqualByComparingTo("0.000436");
        assertThat(rate.cost(100,30,null,10)).isNull();assertThat(rate.cost(100,90,20L,10)).isNull();
    }
    @Test void semanticChecksRemainUnevaluatedAndReportsPreserveEvidence() throws Exception {
        var scenario=QaScenarios.standard(100);
        assertThat(scenario.evaluate(16,"서문이 아니고 동문이야",List.of()).result()).isEqualTo("UNEVALUATED");
        var runner=new QaRunner(dir,QaEnvironment.mockPersona(),new Target());runner.create(QaSpec.mock("report",20),scenario);
        for(int i=0;i<20;i++)runner.step();QaReports.write(dir,runner.read());
        assertThat(java.nio.file.Files.readString(dir.resolve("report.md"))).contains("무료 모의 실행","UNEVALUATED");
        assertThat(java.nio.file.Files.readString(dir.resolve("calls.csv"))).startsWith("call,turn,call_id,role,model");
    }
    @Test void paidExecutionRequiresRunSpecificApprovalAndAllPrices() {
        var mock=QaSpec.mock("paid",20);
        assertThatThrownBy(()->new QaSpec("paid",20,true,null,mock.persona(),"p","t","e",BigDecimal.ONE,BigDecimal.ONE,Map.of(),3,true))
                .hasMessageContaining("approval");
        assertThat(QaSpec.preset("LONG")).isEqualTo(1000);assertThat(QaSpec.preset("MEDIUM")).isEqualTo(500);
    }
    @Test void explicitReconciledRetryPreservesInputAndDoesNotRegeneratePersona() throws Exception {
        var personaCalls=new AtomicInteger();
        var target=new Target(){public QaRunner.Outcome send(String id,String input){if(sends++==0)throw new IllegalStateException("timeout");return receipts.computeIfAbsent(id,key->ok(input));}};
        var runner=new QaRunner(dir,i->{personaCalls.incrementAndGet();return new QaRunner.Generation("저장된 입력",List.of(),null);},target);
        runner.create(QaSpec.mock("retry",20),QaScenarios.standard(20));var failed=runner.step();
        assertThat(failed.unknownCost()).isTrue();
        assertThatThrownBy(()->runner.authorizeRetry(Map.of(),"checked provider")).hasMessageContaining("reconciled");
        var call=failed.calls.getFirst();runner.authorizeRetry(Map.of(call.callId(),BigDecimal.ZERO),"Mock timeout; verified no external API");
        var recovered=runner.step();assertThat(recovered.turns).hasSize(1);assertThat(personaCalls.get()).isEqualTo(1);
        assertThat(recovered.audit).hasSize(1);assertThat(recovered.turns.getFirst().input()).isEqualTo("저장된 입력");
    }
    @Test void mediumAndLongPresetsCompleteWithoutExternalCalls() throws Exception {
        for(int size:List.of(500,1000)) {
            var runner=new QaRunner(dir.resolve("long-"+size),QaEnvironment.mockPersona(),new Target());
            runner.create(QaSpec.mock("long-"+size,size),QaScenarios.standard(size));
            for(int i=0;i<size;i++)runner.step();
            assertThat(runner.read().status).isEqualTo("COMPLETED");
        }
    }
    @Test void reconcileSuccessfulCommittedTurnContinuesWithoutRepeatingIt() throws Exception {
        var target=new Target(){public QaRunner.Outcome send(String id,String input){sends++;return new QaRunner.Outcome("保存された返答",List.of(),null,null,null,null,
                List.of(new QaRunner.Usage("TARGET","model","ROLEPLAY",1,0,null,1,"USAGE_REPORTED",null)),null);}};
        var runner=new QaRunner(dir,QaEnvironment.mockPersona(),target);runner.create(QaSpec.mock("cost-review",1),QaScenarios.standard(1));
        var state=runner.step();assertThat(state.status).isEqualTo("REVIEW_REQUIRED");assertThat(state.turns).hasSize(1);
        runner.authorizeRetry(Map.of(state.calls.getFirst().callId(),BigDecimal.ZERO),"Mock provider; zero external cost verified");
        assertThat(runner.step().status).isEqualTo("COMPLETED");assertThat(target.sends).isEqualTo(1);
    }
    @Test void personaExceptionIsSafeDurableAndNeverAutomaticallyRetried() throws Exception {
        var count=new AtomicInteger();
        var runner=new QaRunner(dir,input->{count.incrementAndGet();throw new IllegalStateException("secret-payload");},new Target());
        runner.create(QaSpec.mock("safe-diagnostics",1),new QaScenarios(List.of()));
        var state=runner.step();
        assertThat(state.status).isEqualTo("REVIEW_REQUIRED");
        assertThat(runner.read().diagnostics).extracting(QaRunner.Diagnostic::phase).contains("PERSONA_REQUEST_STARTED","PERSONA_FAILED");
        assertThat(java.nio.file.Files.readString(dir.resolve("run.json"))).doesNotContain("secret-payload");
        assertThatThrownBy(()->PersonaQaExecutionTest.requireSuccessfulExecution(state)).isInstanceOf(AssertionError.class).hasMessageContaining("REVIEW_REQUIRED");
        runner.step();assertThat(count.get()).isEqualTo(1);
    }
}
