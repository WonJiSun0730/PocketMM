package com.example.projectmm.infrastructure.config;

import org.springframework.core.env.Environment;
import java.nio.file.*;
import java.util.*;

/** Launch the same isolated worker from a prepared deployment without compiling on the web host. */
final class QaWorkerProcess {
    static void isolateEnvironment(Map<String,String> environment) {
        for (String name : List.of("PROD_DB_URL", "PROD_DB_USERNAME", "PROD_DB_PASSWORD",
                "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                "OPENAI_API_KEY", "GEMINI_API_KEY", "MAIL_USERNAME", "MAIL_PASSWORD")) environment.remove(name);
        environment.put("SPRING_PROFILES_ACTIVE", "qa-worker");
    }
    static List<String> command(List<String> original, Environment env, Path workspace) {
        var command = new ArrayList<>(original);
        boolean prepared=env.getProperty("app.qa-ui.precompiled-worker", Boolean.class, false);
        command.add(1, prepared ? "-Xmx96m" : "-Xmx384m");
        command.add(command.size()-1, "-DargLine=-Xms32m -Xmx384m -Djava.io.tmpdir=\"" + workspace.resolve("target") + "\"");
        if (prepared) {
            String test = command.stream().filter(s -> s.startsWith("-Dtest=")).findFirst().orElseThrow().substring(7);
            Path compiled = workspace.resolve("target/test-classes/com/example/projectmm/qa/" + test + ".class");
            if (!Files.isRegularFile(compiled)) throw new IllegalStateException("Prepared QA worker is missing: " + compiled);
            if (!command.getLast().equals("test")) throw new IllegalStateException("Unexpected QA goal");
            command.set(command.size()-1, "surefire:test");
        }
        return List.copyOf(command);
    }
}
