package com.example.projectmm.qa;
import com.example.projectmm.application.qa.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.net.URI;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ExperienceBehaviorTest {
    @Test void preparationStageSummaryCountsMeasuredTurnsNotNestedInvocations() {
        var state=mapper.createObjectNode();var turns=state.putArray("turns");
        for(int i=1;i<=3;i++) {
            var t=turns.addObject();t.put("number",i);var outcome=t.putObject("outcome");outcome.put("reply","reply");
            if(i<3)outcome.put("diagnosticsJson","{\"chatTiming\":{\"source\":\"QA_SERVER\",\"preGenerationMs\":50,\"stages\":{\"CONTEXT_BUILD\":{\"durationMs\":"+(i*10)+",\"count\":2,\"failedCount\":0},\"CONTEXT_MEMORY_RECALL\":{\"durationMs\":0,\"count\":1,\"failedCount\":0}}}}");
        }
        var a=PocketExperienceAnalysis.fullAnalysis(state,mapper.createObjectNode());
        var stage=a.path("chatTiming").path("stages").path("CONTEXT_BUILD");
        assertThat(stage.path("count").asInt()).isEqualTo(2);
        assertThat(stage.path("calls").asInt()).isEqualTo(4);
        assertThat(stage.path("average").asDouble()).isEqualTo(15);
        assertThat(a.path("turns").get(2).path("preparationStages").size()).isZero();
        assertThat(a.path("chatTiming").path("stages").path("CONTEXT_MEMORY_RECALL").path("count").asInt()).isEqualTo(2);
        assertThat(a.path("turns").get(2).path("preGenerationMs").isNull()).isTrue();
    }
    @TempDir Path dir;final ObjectMapper mapper=new ObjectMapper();
    QaSpec spec(){var m=QaSpec.mock("experience-transport",3);var rate=new QaSpec.Rate(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,0,BigDecimal.ONE,BigDecimal.ONE,"MOCK RATE","2026-10-16");return new QaSpec(m.runId(),3,true,m.runId(),m.persona(),"gpt-5.6-luna","gpt-5.6-sol","gpt-5.6-luna",BigDecimal.TEN,new BigDecimal("0.01"),Map.of("gpt-5.6-luna",rate,"gpt-5.6-sol",rate),6,true);}
    ExperienceTransport transport() throws Exception {Files.writeString(dir.resolve("manifest.json"),"{\"maximumOutputTokens\":{\"gpt-5.6-sol\":128000}}");var manifest=(tools.jackson.databind.node.ObjectNode)mapper.readTree(Files.readString(dir.resolve("manifest.json")));manifest.set("budgetPolicy",mapper.valueToTree(ExperienceBudgetPolicy.create(List.of())));Files.writeString(dir.resolve("manifest.json"),mapper.writeValueAsString(manifest));return new ExperienceTransport(spec(),dir,(p,m,j,e)->100);}
    @Test void promptsAreUnchangedOnlyAuthorizedOutputCapIsAddedAndLedgerIsDurable() throws Exception {
        var transport=transport();var request=new MockClientHttpRequest(HttpMethod.POST,URI.create("https://api.openai.com/v1/responses"));request.getHeaders().setBearerAuth("secret-api-key");
        byte[] body="{\"model\":\"gpt-5.6-sol\",\"input\":[{\"role\":\"user\",\"content\":\"실제 서비스 요청\"}],\"text\":{\"format\":{\"name\":\"pocket_turn\"}}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response=transport.intercept(request,body,(req,bytes)-> {
            assertThat(mapper.readTree(bytes).path("input")).isEqualTo(mapper.readTree(body).path("input"));assertThat(mapper.readTree(bytes).path("max_output_tokens").asLong()).isEqualTo(10000);
            assertThat(Files.readString(dir.resolve("ledger.jsonl"))).contains("REQUEST_STARTED");
            return new MockClientHttpResponse("{\"status\":\"completed\",\"usage\":{\"input_tokens\":100,\"input_tokens_details\":{\"cached_tokens\":20,\"cache_write_tokens\":0},\"output_tokens\":80}}".getBytes(),HttpStatus.OK);
        });
        response.close();assertThat(transport.since(Set.of())).hasSize(1);assertThat(transport.since(Set.of()).getFirst().role()).isEqualTo("TARGET");
        assertThat(Files.readString(dir.resolve("ledger.jsonl"))).doesNotContain("secret-api-key");
        assertThat(new ExperienceTransport(spec(),dir).since(Set.of())).hasSize(1);
    }
    @Test void uncertainChargeBlocksAllFollowingRequestsWithoutRetry() throws Exception {
        var transport=transport();var request=new MockClientHttpRequest(HttpMethod.POST,URI.create("https://api.openai.com/v1/responses"));byte[] body="{\"model\":\"gpt-5.6-sol\"}".getBytes();var count=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->transport.intercept(request,body,(req,bytes)->{count.incrementAndGet();throw new java.io.IOException("secret-body");})).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(()->transport.intercept(request,body,(req,bytes)->{count.incrementAndGet();return null;})).hasMessageContaining("automatic retry prohibited");
        assertThat(count.get()).isEqualTo(1);assertThat(Files.readString(dir.resolve("ledger.jsonl"))).doesNotContain("secret-body").contains("java.io.IOException");
        assertThatThrownBy(()->new ExperienceTransport(spec(),dir).intercept(request,body,(req,bytes)->null)).hasMessageContaining("automatic retry prohibited");
    }
    @Test void playerHasNoSyntheticWorldMemoryQuizOrUniformShortInstruction() {
        var context=new ExperiencePlayerContext.View(Map.of("player","아이린 · 침착한 정보상","world","셜록 홈즈의 런던"),List.of());
        String json=mapper.writeValueAsString(ExperiencePersonaClient.request("gpt-5.6-luna",context));
        assertThat(json).contains("아이린","셜록 홈즈","Never control","no target length").doesNotContain("마을 광장","QA 동료","max_output_tokens","짧은 한국어로 출력");
    }
    @Test void costSeparationDistributionsAndShortCandidateDoNotClaimSemanticSuccess() {
        var state=mapper.readTree("""
                {"calls":[{"role":"PERSONA","usd":0.2},{"role":"TARGET","usd":1.0,"turn":1,"input":100,"cached":50,"output":10,"status":"completed"},{"role":"AUXILIARY","usd":0.3}],"turns":[{"number":1,"latencyMillis":2000,"outcome":{"reply":"그래.","evaluationJson":"{}"}}]}
                """);
        var result=PocketExperienceAnalysis.analyze(state);
        assertThat(result.serviceUsd()).isEqualByComparingTo("1.3");assertThat(result.operatingUsd()).isEqualByComparingTo("1.5");
        assertThat(result.replyCharacters().median()).isEqualTo(3);assertThat(result.cacheHitRate()).isEqualTo(0.5);
        assertThat(result.findings()).extracting(PocketExperienceAnalysis.Finding::assessment).doesNotContain("FAIL");
        assertThat(result.turns().getFirst().semanticReview()).isEqualTo("NOT_REVIEWED");
    }
    @Test void relativeShortBiasIsAReviewCandidateRatherThanAFailedRun() {
        var state=mapper.createObjectNode();state.put("status","COMPLETED");var turns=state.putArray("turns");var calls=state.putArray("calls");
        for(int i=1;i<=10;i++) {
            var turn=turns.addObject();turn.put("number",i);turn.putObject("outcome").put("reply","지금은 짧게 답할 상황이다.");
            var call=calls.addObject();call.put("turn",i);call.put("role","TARGET");call.put("status","completed");call.put("input",100);call.put("output",50);call.put("usd",0);
        }
        var manifest=mapper.createObjectNode();manifest.putObject("estimateBasis").put("observedOutputTokens",800);
        var analysis=PocketExperienceAnalysis.fullAnalysis(state,manifest);
        assertThat(analysis.path("findings").valueStream().map(f->f.path("type").asText()).toList()).contains("RELATIVE_SHORT_BIAS");
        assertThat(state.path("status").asText()).isEqualTo("COMPLETED");
    }
    @Test void qaMilestoneAnalysisKeepsOldAndFailedAttemptsOutOfCompletedAverages() {
        var state=mapper.createObjectNode();var turns=state.putArray("turns");
        for(int i=1;i<=3;i++) {
            var t=turns.addObject();t.put("number",i);var outcome=t.putObject("outcome");outcome.put("reply","reply");
            if(i<3)outcome.put("diagnosticsJson","{\"chatTiming\":{\"source\":\"QA_SERVER\",\"firstTokenMs\":5,\"bodyReadyMs\":"+(i*10)+",\"reviewCompleteMs\":30,\"processingCompleteMs\":40}}");
        }
        state.putObject("failedOutcome").put("diagnosticsJson","{\"chatTiming\":{\"source\":\"QA_SERVER\",\"bodyReadyMs\":999,\"reviewCompleteMs\":null,\"processingCompleteMs\":null}}");
        var a=PocketExperienceAnalysis.fullAnalysis(state,mapper.createObjectNode());
        assertThat(a.path("chatTiming").path("firstTokenMs").path("count").asInt()).isEqualTo(2);
        assertThat(a.path("chatTiming").path("firstTokenMs").path("average").asDouble()).isEqualTo(5);
        assertThat(a.path("turns").get(2).path("firstTokenMs").isNull()).isTrue();
        var body=a.path("chatTiming").path("bodyReadyMs");
        assertThat(body.path("count").asInt()).isEqualTo(2);
        assertThat(body.path("min").asLong()).isEqualTo(10);
        assertThat(body.path("max").asLong()).isEqualTo(20);
        assertThat(body.path("average").asDouble()).isEqualTo(15);
        assertThat(a.path("turns").get(2).path("bodyReadyMs").isNull()).isTrue();
        assertThat(a.path("failedAttemptTiming").path("bodyReadyMs").asLong()).isEqualTo(999);
    }
    @Test void zeroGenerationMaximumCannotAuthorizeAnUnboundedPaidRequest() throws Exception {
        Files.writeString(dir.resolve("manifest.json"),"{\"maximumOutputTokens\":{\"gpt-5.6-sol\":0}}");
        var transport=new ExperienceTransport(spec(),dir);var request=new MockClientHttpRequest(HttpMethod.POST,URI.create("https://api.openai.com/v1/responses"));
        var count=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->transport.intercept(request,"{\"model\":\"gpt-5.6-sol\"}".getBytes(),(req,bytes)->{count.incrementAndGet();return null;})).hasMessageContaining("maximum output reservation required");
        assertThat(count.get()).isZero();assertThat(Files.exists(dir.resolve("ledger.jsonl"))).isFalse();
    }
    @Test void realExperienceCanUseLongInputsWhileLegacyMemoryQaKeepsItsLimit() throws Exception {
        String input="가".repeat(5000);
        QaRunner.Persona persona=ignored->new QaRunner.Generation(input,List.of(),null);
        QaRunner.Target target=new QaRunner.Target(){
            public QaRunner.Outcome send(String id,String text){assertThat(text).hasSize(5000);return new QaRunner.Outcome("응답",List.of(),null,null,null,null,List.of(),null);}
            public Optional<QaRunner.Outcome> recover(String id){return Optional.empty();}
        };
        var legacy=new QaRunner(dir.resolve("legacy"),persona,target);legacy.create(QaSpec.mock("legacy-input",1),new QaScenarios(List.of()));
        assertThat(legacy.step().status).isEqualTo("REVIEW_REQUIRED");
        var experience=new QaRunner(dir.resolve("experience"),persona,target,0);experience.create(QaSpec.mock("experience-input",1),new QaScenarios(List.of()));
        var state=experience.step();assertThat(state.status).isEqualTo("COMPLETED");assertThat(state.turns.getFirst().input()).isEqualTo(input);
    }
}
