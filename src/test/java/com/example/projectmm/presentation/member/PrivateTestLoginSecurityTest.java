package com.example.projectmm.presentation.member;

import com.example.projectmm.application.member.*;
import com.example.projectmm.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitWebConfig(PrivateTestLoginSecurityTest.Config.class)
@TestPropertySource(properties = {"app.private-test.enabled=true", "spring.mail.username=sender@example.com"})
class PrivateTestLoginSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, PrivateTestLoginController.class, PrivateTestLoginService.class, AuthController.class, Probe.class})
    static class Config {
        @Bean MemberUserDetailsService users(PasswordEncoder encoder) {
            var users = mock(MemberUserDetailsService.class);
            when(users.loadUserByUsername("devseed")).thenReturn(User.withUsername("devseed")
                    .password(encoder.encode("devseed-password")).roles("DEVELOPER").build());
            return users;
        }
        @Bean MemberSignupService signup() { return mock(MemberSignupService.class); }
        @Bean JavaMailSender mail() { return mock(JavaMailSender.class); }
    }
    @RestController static class Probe {
        @GetMapping("/api/private-probe") String probe() { return "private"; }
    }
    @Autowired WebApplicationContext context;
    @Autowired JavaMailSender mail;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void fullLoginRequiresEmailThenRotatesSessionAndLogoutRevokes() throws Exception {
        var session = new MockHttpSession(); String oldId = session.getId();
        mvc.perform(post("/api/auth/login").session(session).param("loginId", "devseed").param("password", "devseed-password"))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/private-probe").session(session)).andExpect(status().isUnauthorized());
        var captured = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captured.capture());
        String code = captured.getValue().getText().replaceAll("(?s).*: ([0-9]{6}).*", "$1");
        mvc.perform(post("/api/auth/verify-login").session(session).contentType("application/json").content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(oldId);
        mvc.perform(get("/api/private-probe").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/verify-login").session(session).contentType("application/json").content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/private-probe")).andExpect(status().isUnauthorized());
    }
    @Test void deniesSignupAndLegacySessionsAndWrongAccount() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/auth/check-email").param("email", "x@example.com")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/private-probe").with(user("devseed"))).andExpect(status().isForbidden());
        var legacy = new MockHttpSession(); legacy.setAttribute(PrivateTestLoginService.VERIFIED, true);
        mvc.perform(get("/api/private-probe").session(legacy).with(user("other"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").param("loginId", "other").param("password", "devseed-password"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(false));
    }
}
