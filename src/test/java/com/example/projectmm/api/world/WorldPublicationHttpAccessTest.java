package com.example.projectmm.api.world;

import com.example.projectmm.application.world.*;
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
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(WorldPublicationHttpAccessTest.Config.class)
@TestPropertySource(properties="app.private-test.enabled=false")
class WorldPublicationHttpAccessTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, WorldPublicationController.class})
    static class Config {
        @Bean WorldPublicationService publication() { return mock(WorldPublicationService.class); }
        @Bean WorldCoverService covers() { return mock(WorldCoverService.class); }
    }
    @Autowired WebApplicationContext context;
    @Autowired WorldPublicationService publication;
    @Autowired WorldCoverService covers;

    @Test void anonymousCannotReadOrStartPublishedWorldsOrReadCovers() throws Exception {
        clearInvocations(publication, covers);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String route : List.of("/api/published-worlds", "/api/published-worlds/1", "/api/published-worlds/1/cover/a.png", "/api/worlds/1/publication"))
            mvc.perform(get(route)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/published-worlds/1/start").contentType("application/json").content("{\"introId\":1}"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(publication, covers);
    }
    @Test void authenticatedReaderGetsOnlyListingAndValidatedStartRequest() throws Exception {
        clearInvocations(publication, covers);
        when(publication.list(0)).thenReturn(new WorldPublicationService.Listing(List.of(), 0, false));
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/published-worlds").with(user("reader"))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(post("/api/published-worlds/1/start").with(user("reader")).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verify(publication).list(0);
        verify(publication, never()).start(any(), any(), any(), any());
    }
}
