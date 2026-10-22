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
    @Test void costlyModelsAreRejectedBeforeWorkerLaunch() throws Exception {
        var worker = new Worker(); var service = service(worker);
        assertThat(ExperienceQaUiService.TARGET_MODELS).containsExactly("gpt-5.6-luna");
        for (String model : List.of("gpt-5.6-sol", "gpt-5.6-terra"))
            assertThatThrownBy(() -> service.start(1,"dev",new ExperienceQaUiService.Start(
                    UUID.randomUUID().toString(),model,100,false,BigDecimal.ZERO,false,DIGEST)))
                    .hasMessageContaining("Luna");
        assertThat(worker.launches).isZero();
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
    ExperienceQaUiService.Start start(String digest){return new ExperienceQaUiService.Start(UUID.randomUUID().toString(),"gpt-5.6-luna",3,false,BigDecimal.ZERO,false,digest);}
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
