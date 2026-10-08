package com.example.projectmm.presentation.member;

import com.example.projectmm.application.member.MemberSignupService;
import com.example.projectmm.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(RegularLoginSecurityTest.Config.class)
@TestPropertySource(properties = "app.private-test.enabled=false")
class RegularLoginSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, AuthController.class})
    static class Config {
        @Bean UserDetailsService users(PasswordEncoder encoder) {
            return new InMemoryUserDetailsManager(User.withUsername("other")
                    .password(encoder.encode("password123")).roles("USER").build());
        }
        @Bean MemberSignupService signup() { return mock(MemberSignupService.class); }
    }
    @Autowired WebApplicationContext context;
    @Test void normalModeKeepsOtherAccountsAndSignupAvailability() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/auth/login").param("loginId", "other").param("password", "password123"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(true))
                .andExpect(jsonPath("$.emailVerificationRequired").value(false));
        mvc.perform(get("/api/auth/check-email").param("email", "x@example.com"))
                .andExpect(status().isOk());
    }
}
