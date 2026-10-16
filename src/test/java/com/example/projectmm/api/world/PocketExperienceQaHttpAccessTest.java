package com.example.projectmm.api.world;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.application.qa.ExperienceQaUiService;
import com.example.projectmm.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(PocketExperienceQaHttpAccessTest.Config.class)
@TestPropertySource(properties={"app.private-test.enabled=false","app.qa-ui.enabled=true"})
class PocketExperienceQaHttpAccessTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,PocketExperienceQaController.class})
    static class Config {
        @Bean ExperienceQaUiService qa(){return mock(ExperienceQaUiService.class);}
        @Bean WorldRoomSessionService sessions(){return mock(WorldRoomSessionService.class);}
    }
    @Autowired WebApplicationContext context;
    @Autowired ExperienceQaUiService qa;
    @Autowired WorldRoomSessionService sessions;
    @Test void authenticatedOrdinaryUserCannotCallAnyQaRoute() throws Exception {
        clearInvocations(qa,sessions);
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String base="/api/worlds/pockets/1/experience-qa";
        for(var suffix:List.of("/capabilities","/runs","/runs/ui-123","/runs/ui-123/report","/runs/ui-123/analysis","/runs/ui-123/conversation"))
            mvc.perform(get(base+suffix).with(user("member").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post(base+"/runs").with(user("member").roles("USER")).contentType("application/json")
                .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"targetModel\":\"gpt-5.6-sol\",\"turns\":20,\"paid\":false,\"budgetUsd\":0,\"acknowledgeCost\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base+"/runs/ui-123/stop").with(user("member").roles("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(qa,sessions);
    }
    @Test void developerCanReadCapabilitiesOnlyAfterPocketOwnershipCheck() throws Exception {
        clearInvocations(qa,sessions);
        when(qa.capabilities(1L,"dev")).thenReturn(new ExperienceQaUiService.Capabilities("gpt-5.6-luna",ExperienceQaUiService.TARGET_MODELS,false,"missing key",1000,Map.of(),new ExperienceQaUiService.SnapshotInfo("digest","셜록",List.of("홈즈"),"아이린",2,1,1000,600)));
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/worlds/pockets/1/experience-qa/capabilities").with(user("dev").roles("DEVELOPER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personaModel").value("gpt-5.6-luna"));
        verify(sessions).pocket(1L,"dev");
    }
}
