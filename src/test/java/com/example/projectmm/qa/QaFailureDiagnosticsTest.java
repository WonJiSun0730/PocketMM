package com.example.projectmm.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QaFailureDiagnosticsTest {
    @TempDir Path dir;
    @Test void keepsLocalValidationReasonWithoutProviderMessageSecrets() {
        var local=new IllegalStateException("Scene continuity: Fact predecessor does not match");
        local.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                "com.example.projectmm.domain.dialogue.SceneFacts","apply","SceneFacts.java",44)});
        var outer=new IllegalStateException("Bearer SECRET and private prompt",local);
        String saved=new ObjectMapper().writeValueAsString(QaPipeline.failureDetails(outer));
        assertThat(saved).contains("Fact predecessor does not match","SceneFacts.java:44")
                .doesNotContain("SECRET","private prompt");
    }
    @Test void persistsFailedOutcomeWithoutCountingItAsCompletedOrRetrying() throws Exception {
        var sends=new int[]{0};
        QaRunner.Target target=new QaRunner.Target() {
            public QaRunner.Outcome send(String id,String input) {
                sends[0]++;
                return new QaRunner.Outcome("",List.of(),null,"{\"phase\":\"TARGET_LOCAL_FAILURE\"}",
                        null,null,List.of(),"Target failed; no automatic retry.");
            }
            public Optional<QaRunner.Outcome> recover(String id){return Optional.empty();}
        };
        var runner=new QaRunner(dir,QaEnvironment.mockPersona(),target);
        runner.create(QaSpec.mock("failure-details",3),QaScenarios.standard(3));
        runner.step();
        var saved=runner.read();
        assertThat(saved.status).isEqualTo("REVIEW_REQUIRED");
        assertThat(saved.turns).isEmpty();
        assertThat(saved.failedOutcome.diagnosticsJson()).contains("TARGET_LOCAL_FAILURE");
        runner.step();assertThat(sends[0]).isEqualTo(1);
    }
}
