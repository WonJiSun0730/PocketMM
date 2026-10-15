package com.example.projectmm.qa;

import com.example.projectmm.application.qa.QaUiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QaUiServiceTest {
    @TempDir Path dir;
    static class Worker implements QaUiService.Worker {
        int launches;boolean alive=true;List<String> command;Map<String,String> environment;
        public long launch(List<String> command,Path root,Map<String,String> environment,Path log){launches++;this.command=command;this.environment=environment;return 12345;}
        public boolean alive(long pid,String created){return alive;}
    }
    QaUiService service(Worker worker) throws Exception {
        Files.createDirectories(dir.resolve("tools"));Files.writeString(dir.resolve("tools/persona-qa.ps1"),"# fixture");
        var source=dir.resolve("src/test/java/com/example/projectmm/qa/PersonaQaExecutionTest.java");Files.createDirectories(source.getParent());Files.writeString(source,"// fixture");
        Files.createDirectories(dir.resolve("maven/boot"));Files.createDirectories(dir.resolve("maven/bin"));Files.writeString(dir.resolve("maven/boot/plexus-classworlds-test.jar"),"");
        return new QaUiService(dir,null,dir.resolve("maven").toString(),"","",worker,new ObjectMapper());
    }
    QaUiService.Start free(String id) {return new QaUiService.Start(id,"gpt-5.6-sol",20,false,BigDecimal.ZERO,false);}
    @Test void startIsIdempotentPinsLunaAndDoesNotCopyRealPocketData() throws Exception {
        var worker=new Worker();var service=service(worker);var request=free(UUID.randomUUID().toString());
        var first=service.start(7L,"developer",request);var again=service.start(7L,"developer",request);
        assertThat(first.id()).isEqualTo(again.id());assertThat(worker.launches).isEqualTo(1);
        var config=new ObjectMapper().readTree(Files.readString(dir.resolve("target/pocket-qa").resolve(first.id()).resolve("config.json")));
        assertThat(config.path("personaModel").asText()).isEqualTo("gpt-5.6-luna");assertThat(config.path("targetModel").asText()).isEqualTo("gpt-5.6-sol");
        assertThat(config.toString()).doesNotContain("developer","pocketId");assertThat(config.path("paid").asBoolean()).isFalse();
        assertThat(worker.command).noneMatch(c->c.startsWith("-Dpocket.qa.approve="));assertThat(worker.environment.get("POCKET_QA_OPENAI_API_KEY")).isEmpty();
        assertThatThrownBy(()->service.start(7L,"developer",new QaUiService.Start(request.requestId(),"gpt-5.6-luna",100,false,BigDecimal.ZERO,false))).hasMessageContaining("409");
        assertThatThrownBy(()->service.current(7L,"another",first.id())).hasMessageContaining("404");
        assertThatThrownBy(()->service.current(8L,"developer",first.id())).hasMessageContaining("404");
        assertThat(service.list(7L,"another")).isEmpty();
    }
    @Test void paidRunRequiresConsentPricesAndKeyAndUnknownModelsAreRejected() throws Exception {
        var worker=new Worker();var service=service(worker);
        assertThatThrownBy(()->service.start(1L,"dev",new QaUiService.Start(UUID.randomUUID().toString(),"gpt-5.6-sol",20,true,BigDecimal.ONE,false))).hasMessageContaining("동의");
        assertThatThrownBy(()->service.start(1L,"dev",new QaUiService.Start(UUID.randomUUID().toString(),"gpt-5.6-sol",20,true,BigDecimal.ONE,true))).hasMessageContaining("API 키");
        assertThatThrownBy(()->service.start(1L,"dev",new QaUiService.Start(UUID.randomUUID().toString(),"malicious",20,false,BigDecimal.ZERO,false))).hasMessageContaining("대상 모델");
        assertThatThrownBy(()->service.start(1L,"dev",new QaUiService.Start(UUID.randomUUID().toString(),"gpt-5.6-sol",1001,false,BigDecimal.ZERO,false))).hasMessageContaining("1~1,000");
        assertThat(worker.launches).isZero();assertThatThrownBy(()->service.current(1L,"dev","../../config")).hasMessageContaining("400");
    }
    @Test void statusReadsExistingCheckpointAndSafeControlsDoNotLaunchDuplicateWorker() throws Exception {
        var worker=new Worker();var service=service(worker);var run=service.start(1L,"dev",free(UUID.randomUUID().toString()));
        Path path=dir.resolve("target/pocket-qa").resolve(run.id());
        Files.writeString(path.resolve("run.json"),"""
                {"status":"RUNNING","turns":[],"calls":[{"callId":"a","usd":null}],"reason":null}
                """);
        assertThat(service.current(1L,"dev",run.id()).unknownCalls()).isEqualTo(1);
        assertThat(service.control(1L,"dev",run.id(),"PAUSE").status()).isEqualTo("PAUSING");
        assertThat(worker.launches).isEqualTo(1);
        worker.alive=false;assertThat(service.current(1L,"dev",run.id()).status()).isEqualTo("INTERRUPTED");
        service.control(1L,"dev",run.id(),"STOP");assertThat(service.current(1L,"dev",run.id()).status()).isEqualTo("STOPPED");
        assertThatThrownBy(()->service.control(1L,"dev",run.id(),"RESUME")).hasMessageContaining("409");
    }
    @Test void paidConfigurationUsesVerifiedFileRatesAndExplicitApproval() throws Exception {
        var worker=new Worker();service(worker);
        Path prices=dir.resolve("prices.json");Files.copy(Path.of("tools/qa-ui-prices.example.json"),prices);
        var service=new QaUiService(dir,prices,dir.resolve("maven").toString(),"cache","never-sent-test-key",worker,new ObjectMapper());
        assertThat(service.capabilities().paidAvailable()).isTrue();
        var run=service.start(1L,"dev",new QaUiService.Start(UUID.randomUUID().toString(),"gpt-5.6-sol",20,true,new BigDecimal("2"),true));
        assertThat(worker.command).contains("-Dpocket.qa.approve="+run.id());
        var config=new ObjectMapper().readTree(Files.readString(dir.resolve("target/pocket-qa").resolve(run.id()).resolve("config.json")));
        assertThat(config.toString()).doesNotContain("never-sent-test-key");
        assertThat(config.path("prices").path("gpt-5.6-sol").path("written").decimalValue()).isEqualByComparingTo("5");
    }
    @Test void pausedWorkerCanResumeWithSameRunAndSameConfiguration() throws Exception {
        var worker=new Worker();var service=service(worker);var run=service.start(1L,"dev",free(UUID.randomUUID().toString()));
        Path path=dir.resolve("target/pocket-qa").resolve(run.id());
        var config=Files.readString(path.resolve("config.json"));
        Files.writeString(path.resolve("run.json"),"{\"status\":\"PAUSED\",\"turns\":[],\"calls\":[]}");worker.alive=false;
        service.control(1L,"dev",run.id(),"RESUME");
        assertThat(worker.launches).isEqualTo(2);assertThat(Files.readString(path.resolve("control"))).isEqualTo("RESUME");
        assertThat(Files.readString(path.resolve("config.json"))).isEqualTo(config);
    }
    @Test void unreadableProgressCannotPreventStopOrTurnUnknownCostIntoZero() throws Exception {
        var worker=new Worker();var service=service(worker);var run=service.start(1L,"dev",free(UUID.randomUUID().toString()));
        var path=dir.resolve("target/pocket-qa").resolve(run.id());Files.writeString(path.resolve("run.json"),"invalid-json");
        var stopped=service.control(1L,"dev",run.id(),"STOP");
        assertThat(Files.readString(path.resolve("control"))).isEqualTo("STOP");assertThat(stopped.status()).isEqualTo("STOPPING");
        assertThat(stopped.knownUsd()).isNull();assertThat(stopped.unknownCalls()).isEqualTo(1);assertThat(worker.launches).isEqualTo(1);
    }
}
