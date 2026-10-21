package com.example.projectmm.api.world;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PocketQaProductionDefaultTest {
    @Test void productionStillExcludesBothControllersWithoutExplicitOptInProfile() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("app.qa-ui.enabled","true")));
            context.register(PocketQaController.class,PocketExperienceQaController.class);
            context.refresh();
            assertThat(context.getBeansOfType(PocketQaController.class)).isEmpty();
            assertThat(context.getBeansOfType(PocketExperienceQaController.class)).isEmpty();
        }
    }
}
