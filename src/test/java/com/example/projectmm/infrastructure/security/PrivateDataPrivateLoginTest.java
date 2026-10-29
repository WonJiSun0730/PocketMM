package com.example.projectmm.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(PrivateDataHttpSecurityTest.Config.class)
@TestPropertySource(properties="app.private-test.enabled=true")
class PrivateDataPrivateLoginTest {
    @Autowired WebApplicationContext context;
    @Test void operatorRouteKeepsPrivateEmailVerificationAndAccountRestriction() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var session=new MockHttpSession();
        mvc.perform(get("/api/admin/private-data/probe").with(user("devseed").roles("DEVELOPER")).session(session)).andExpect(status().isForbidden());
        session.setAttribute(com.example.projectmm.application.member.PrivateTestLoginService.VERIFIED,true);
        mvc.perform(get("/api/admin/private-data/probe").with(user("other").roles("ADMIN")).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/private-data/probe").with(user("devseed").roles("DEVELOPER")).session(session)).andExpect(status().isOk());
    }
}
