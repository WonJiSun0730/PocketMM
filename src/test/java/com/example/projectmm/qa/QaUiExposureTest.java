package com.example.projectmm.qa;

import com.example.projectmm.api.world.PocketQaController;
import com.example.projectmm.application.qa.QaUiService;
import com.example.projectmm.infrastructure.config.QaUiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class QaUiExposureTest {
    @Test void disabledByDefaultAndExcludedFromProductionEvenIfFlagIsTrue() {
        var runner=new ApplicationContextRunner().withUserConfiguration(QaUiConfiguration.class,PocketQaController.class);
        runner.run(context->{assertThat(context).doesNotHaveBean(QaUiService.class);assertThat(context).doesNotHaveBean(PocketQaController.class);});
        for(var profile:java.util.List.of("prod","production"))runner.withPropertyValues("app.qa-ui.enabled=true")
                .withInitializer(context->context.getEnvironment().setActiveProfiles(profile))
                .run(context->{assertThat(context).doesNotHaveBean(QaUiService.class);assertThat(context).doesNotHaveBean(PocketQaController.class);});
    }
}
