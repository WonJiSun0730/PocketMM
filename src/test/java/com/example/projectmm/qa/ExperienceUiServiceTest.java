package com.example.projectmm.qa;
import com.example.projectmm.application.qa.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExperienceUiServiceTest {
    @Test void metricsDownloadIncludesQaMilestonesAndLeavesLegacyValuesBlank() throws Exception {
        var service=service(new Worker());var run=service.start(1,"dev",start(DIGEST));
        var mapper=new ObjectMapper();var state=mapper.createObjectNode();state.put("status","COMPLETED");
        state.putArray("calls");
        var turns=state.putArray("turns");
        var first=turns.addObject();first.put("number",1);
        first.putObject("outcome").put("reply","reply").put("diagnosticsJson",
                "{\"chatTiming\":{\"source\":\"QA_SERVER\",\"bodyReadyMs\":100,\"reviewCompleteMs\":200,\"processingCompleteMs\":300,\"preGenerationMs\":60,\"targetDispatchMs\":80,\"stages\":{\"TIME_INTERPRETATION\":{\"durationMs\":20,\"count\":1,\"failedCount\":0}}}}");
        turns.addObject().put("number",2).putObject("outcome").put("reply","old reply");
        Files.writeString(dir.resolve("target/pocket-qa-experience").resolve(run.id()).resolve("run.json"),mapper.writeValueAsString(state));
        String csv=new String(service.download(1,"dev",run.id(),"metrics"),java.nio.charset.StandardCharsets.UTF_8);
        var rows=csv.lines().toList();
        assertThat(rows.getFirst()).contains("qa_body_ready_ms,qa_review_complete_ms,qa_processing_complete_ms,qa_pre_generation_ms,qa_target_dispatch_ms","qa_stage_time_interpretation_ms");
        assertThat(Arrays.asList(rows.get(1).split(",",-1)).subList(11,14)).containsExactly("100","200","300");
        assertThat(Arrays.asList(rows.get(1).split(",",-1)).subList(14,16)).containsExactly("60","80");
        int stageColumn=Arrays.asList(rows.getFirst().split(",")).indexOf("qa_stage_time_interpretation_ms");
        assertThat(rows.get(1).split(",",-1)[stageColumn]).isEqualTo("20");
        assertThat(Arrays.asList(rows.get(2).split(",",-1)).subList(11,14)).containsExactly("","","");
        String json=new String(service.download(1,"dev",run.id(),"json"),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).contains("bodyReadyMs","processingCompleteMs","QA_SERVER");
    }
    @Test void livePendingCallSettlesFromLatestLedgerAndDeadWorkerRemainsUnknown() throws Exception {
        var alive=new boolean[]{true};
        var worker=new Worker(){@Override public boolean alive(long pid,String at){return alive[0];}};
        var service=service(worker);var run=service.start(1,"dev",start(DIGEST));
        var path=dir.resolve("target/pocket-qa-experience").resolve(run.id());
        Files.writeString(path.resolve("run.json"),"{\"status\":\"RUNNING\",\"stage\":\"TARGET_IN_FLIGHT\",\"turns\":[],\"calls\":[{\"callId\":\"call\",\"turn\":1,\"phase\":\"REQUEST_STARTED\",\"role\":\"TARGET\",\"status\":\"UNKNOWN\",\"usd\":null}]}");
        String started="{\"callId\":\"call\",\"phase\":\"REQUEST_STARTED\",\"role\":\"TARGET\",\"status\":\"UNKNOWN\",\"usd\":null}\n";
        Files.writeString(path.resolve("ledger.jsonl"),started);
        assertThat(service.current(1,"dev",run.id()).pendingCalls()).isEqualTo(1);
        assertThat(service.analysis(1,"dev",run.id()).path("findings").toString()).doesNotContain("CALL_STATUS");
        alive[0]=false;
        assertThat(service.current(1,"dev",run.id()).pendingCalls()).isZero();
        assertThat(service.current(1,"dev",run.id()).unknownCalls()).isEqualTo(1);
        assertThat(service.analysis(1,"dev",run.id()).path("findings").toString()).contains("CALL_STATUS");
        Files.writeString(path.resolve("ledger.jsonl"),started+"{\"callId\":\"call\",\"phase\":\"RESPONSE_RECEIVED\",\"role\":\"TARGET\",\"status\":\"completed\",\"usd\":0.02}\n");
        var settled=service.current(1,"dev",run.id());
        assertThat(settled.unknownCalls()).isZero();assertThat(settled.pendingCalls()).isZero();
        assertThat(settled.knownUsd()).isEqualByComparingTo("0.02");
    }
    static final String DIGEST=PocketExperienceSnapshot.digest(List.of());
    @TempDir Path dir;
    static class Worker implements ExperienceQaUiService.Worker {
        int launches;public long launch(List<String> command,Path directory,Map<String,String> env,Path log){launches++;assertThat(command).contains("-Dpocket.experience.execute=true","-Dtest=ExperienceExecutionTest");return 42;}
        public boolean alive(long pid,String at){return true;}
    }
    ExperienceQaUiService service(Worker worker) throws Exception {
        Files.createDirectories(dir.resolve("tools"));Files.writeString(dir.resolve("tools/persona-qa.ps1"),"fixture");
        var test=dir.resolve("src/test/java/com/example/projectmm/qa/ExperienceExecutionTest.java");Files.createDirectories(test.getParent());Files.writeString(test,"fixture");
        Files.createDirectories(dir.resolve("maven/boot"));Files.writeString(dir.resolve("maven/boot/plexus-classworlds-test.jar"),"");
        var snapshots=mock(PocketExperienceSnapshotService.class);
        when(snapshots.capture(1L,"dev")).thenReturn(new PocketExperienceSnapshot(1,"2026-10-16T00:00:00Z",DIGEST,"NONE","gpt-5.6-luna",null,null,null,false,List.of(),List.of("mock-only")));
        return new ExperienceQaUiService(dir,null,dir.resolve("maven").toString(),"","",worker,new ObjectMapper(),snapshots,null,"");
    }
    ExperienceQaUiService.Start start(String digest){return new ExperienceQaUiService.Start(UUID.randomUUID().toString(),"gpt-5.6-sol",3,false,BigDecimal.ZERO,false,digest);}
    @Test void stalePocketIsRejectedBeforeAnyWorkerAndSameRequestIsIdempotent() throws Exception {
        var worker=new Worker();var service=service(worker);
        assertThatThrownBy(()->service.start(1,"dev",start("stale"))).hasMessageContaining("409");assertThat(worker.launches).isZero();
        var request=start(DIGEST);var result=service.start(1,"dev",request);assertThat(result.id()).startsWith("exp-");
        assertThat(service.start(1,"dev",request).id()).isEqualTo(result.id());assertThat(worker.launches).isEqualTo(1);
        assertThat(Files.exists(dir.resolve("target/pocket-qa"))).isFalse();
        assertThat(Files.readString(dir.resolve("target/pocket-qa-experience").resolve(result.id()).resolve("manifest.json"))).contains("codeHashes",DIGEST,"MANUAL_NO_MODEL_CALLS");
    }
    @Test void fullConversationPagingReviewsAndUnknownInflightCostAreVisible() throws Exception {
        var service=service(new Worker());var run=service.start(1,"dev",start(DIGEST));var path=dir.resolve("target/pocket-qa-experience").resolve(run.id());
        Files.writeString(path.resolve("run.json"),"{\"status\":\"RUNNING\",\"turns\":[{\"number\":1,\"input\":\"안녕\",\"outcome\":{\"reply\":\"반갑네.\"}}],\"calls\":[]}");
        Files.writeString(path.resolve("ledger.jsonl"),"{\"callId\":\"inflight\",\"phase\":\"REQUEST_STARTED\",\"role\":\"TARGET\",\"usd\":null}\n");
        assertThat(service.current(1,"dev",run.id()).unknownCalls()).isEqualTo(1);
        assertThat(service.conversation(1,"dev",run.id(),0,20).path("turns").get(0).path("input").asText()).isEqualTo("안녕");
        service.review(1,"dev",run.id(),new ExperienceQaUiService.Review(1,"CHARACTER_DRIFT","원전 말투와 다른 사례"));
        assertThat(service.analysis(1,"dev",run.id()).path("reviews").size()).isEqualTo(1);
        assertThatThrownBy(()->service.review(1,"other",run.id(),new ExperienceQaUiService.Review(1,"MEMORY_ERROR","기억 오류"))).hasMessageContaining("404");
        assertThatThrownBy(()->service.review(1,"dev",run.id(),new ExperienceQaUiService.Review(2,"MEMORY_ERROR","미래 턴"))).hasMessageContaining("400");
    }
}
