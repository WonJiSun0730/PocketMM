package com.example.projectmm.infrastructure.config;

import com.example.projectmm.application.qa.ExperienceQaUiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;

@Configuration(proxyBeanMethods=false)
@Profile("(!prod & !production) | qa-ui")
@ConditionalOnProperty(name="app.qa-ui.enabled",havingValue="true")
public class ExperienceQaUiConfiguration {
    @Bean com.example.projectmm.application.qa.PocketExperienceSnapshotService experienceSnapshots(
            com.example.projectmm.application.chat.WorldRoomSessionService sessions,org.springframework.jdbc.core.JdbcTemplate jdbc,Environment env) {
        return new com.example.projectmm.application.qa.PocketExperienceSnapshotService(sessions,jdbc,env);
    }
    @Bean ExperienceQaUiService experienceQaUiService(Environment env,ObjectMapper mapper,com.example.projectmm.application.qa.PocketExperienceSnapshotService snapshots) throws java.io.IOException {
        String root=env.getProperty("app.qa-ui.workspace",".");
        String prices=env.getProperty("app.qa-ui.prices-file");
        return new ExperienceQaUiService(Path.of(root),prices==null?null:Path.of(prices),
                env.getProperty("app.qa-ui.maven",System.getProperty("maven.home",".\\mvnw.cmd")),env.getProperty("app.qa-ui.maven-cache",""),
                env.getProperty("app.qa-ui.api-key",env.getProperty("openai.api-key","")),new ExperienceQaUiService.Worker() {
                    public long launch(java.util.List<String> command,Path directory,java.util.Map<String,String> environment,Path log) throws java.io.IOException {
                        var channel=java.nio.channels.FileChannel.open(directory.resolve("target/pocket-qa/ui-worker-global.lock"),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE);
                        var lock=channel.tryLock();if(lock==null){channel.close();throw new java.io.IOException("Another UI QA worker is active");}
                        var builder=new ProcessBuilder(QaWorkerProcess.command(command, env, directory)).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
                        try {
                            QaWorkerProcess.isolateEnvironment(builder.environment());builder.environment().putAll(environment);var process=builder.start();
                            process.onExit().whenComplete((p,e)->{try{lock.release();channel.close();}catch(java.io.IOException ignored){}});
                            return process.pid();
                        } catch(java.io.IOException|RuntimeException ex) {lock.release();channel.close();throw ex;}
                    }
                    public boolean alive(long pid,String createdAt) {
                        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).filter(p->p.info().startInstant()
                                .map(start->java.time.Duration.between(Instant.parse(createdAt),start).abs().compareTo(java.time.Duration.ofSeconds(10))<=0).orElse(false)).isPresent();
                    }
                },mapper,snapshots,env.getProperty("app.experience-qa.model-limits-file")==null?Path.of(root).resolve("tools/experience-qa-model-limits.json"):Path.of(env.getProperty("app.experience-qa.model-limits-file")),env.getProperty("gemini.api-key",""));
    }
}
