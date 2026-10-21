package com.example.projectmm.qa;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.nio.file.Path;
import java.util.Map;

/** Override datasource configuration BEFORE auto-configuration; never accept an operator DB URL. */
public final class ExperienceDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        String id=System.getProperty("pocket.qa.run-id","");
        if(!id.matches("[a-zA-Z0-9_-]{1,60}"))throw new IllegalArgumentException("Valid QA run-id is required");
        Path root=Path.of("target/pocket-qa-experience").toAbsolutePath().normalize();
        Path db=root.resolve(id).resolve("database").normalize();
        if(!db.startsWith(root))throw new IllegalArgumentException("QA path escaped workspace");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-qa-database",Map.ofEntries(
                Map.entry("spring.datasource.url","jdbc:h2:file:"+db.toString().replace('\\','/')+";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE;MAX_COMPACT_TIME=0"),
                Map.entry("spring.datasource.driver-class-name","org.h2.Driver"),Map.entry("spring.datasource.username","sa"),Map.entry("spring.datasource.password",""),
                Map.entry("spring.jpa.hibernate.ddl-auto","update"),Map.entry("spring.jpa.properties.hibernate.dialect","org.hibernate.dialect.H2Dialect"),
                Map.entry("spring.sql.init.mode","never"),Map.entry("gemini.api-key",""),Map.entry("openai.api-key",""),Map.entry("chat.memory.enabled",memoryEnabled())
        )));
    }
    private boolean memoryEnabled() {
        try{return ExperienceExecutionTest.snapshot().memoryEnabled();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
}
