package com.example.projectmm.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QaWorkerProcessTest {
    @TempDir Path root;
    final List<String> input=List.of("java","-o","-Dtest=ExperienceExecutionTest","test");
    @Test void workerDoesNotInheritProductionDatabaseOrProviderSecrets() {
        var env=new HashMap<>(Map.of("PROD_DB_PASSWORD","secret","OPENAI_API_KEY","key",
                "SPRING_PROFILES_ACTIVE","prod,qa-ui","PATH","system-path"));
        QaWorkerProcess.isolateEnvironment(env);
        assertThat(env).doesNotContainKeys("PROD_DB_PASSWORD","OPENAI_API_KEY");
        assertThat(env).containsEntry("SPRING_PROFILES_ACTIVE","qa-worker").containsEntry("PATH","system-path");
    }
    @Test void defaultKeepsCompilationAndBoundsMemory() {
        var result=QaWorkerProcess.command(input,new MockEnvironment(),root);
        assertThat(result.getLast()).isEqualTo("test");
        assertThat(result).contains("-Xmx384m");
        assertThat(input).hasSize(4);
    }
    @Test void precompiledModeRequiresInstalledClasses() throws Exception {
        var env=new MockEnvironment().withProperty("app.qa-ui.precompiled-worker","true");
        assertThatThrownBy(()->QaWorkerProcess.command(input,env,root)).hasMessageContaining("missing");
        var file=root.resolve("target/test-classes/com/example/projectmm/qa/ExperienceExecutionTest.class");
        Files.createDirectories(file.getParent());Files.write(file,new byte[]{1});
        var command=QaWorkerProcess.command(input,env,root);
        assertThat(command.getLast()).isEqualTo("surefire:test");
        assertThat(command).contains("-Xmx96m");
    }
}
