package com.example.projectmm.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @org.springframework.beans.factory.annotation.Value("${app.private-test.enabled:false}") boolean privateTest
    ) throws Exception {

        if (privateTest) {
            http.csrf(csrf -> csrf.disable())
                    .formLogin(form -> form.disable())
                    .requestCache(cache -> cache.disable())
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                            .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/verify-login").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/auth/options", "/actuator/health").permitAll()
                            .requestMatchers("/api/auth/signup", "/api/auth/check-login-id", "/api/auth/check-email").denyAll()
                            .requestMatchers("/api/**").access((authentication, context) -> {
                                var session = context.getRequest().getSession(false);
                                var principal = authentication.get();
                                return new org.springframework.security.authorization.AuthorizationDecision(
                                        principal != null && principal.isAuthenticated()
                                        && "devseed".equals(principal.getName()) && session != null
                                        && Boolean.TRUE.equals(session.getAttribute(
                                                com.example.projectmm.application.member.PrivateTestLoginService.VERIFIED)));
                            })
                            .anyRequest().permitAll())
                    .logout(logout -> logout.logoutUrl("/api/auth/logout")
                            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
            return http.build();
        }

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/auth/signup",
                                        "/api/auth/login"
                                )
                                .permitAll()
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/auth/check-login-id",
                                        "/api/auth/check-email",
                                        "/actuator/health"
                                )
                                .permitAll()
                                 .requestMatchers(
                                        "/api/auth/me",
                                        "/api/characters/**",
                                        "/api/worlds/**",
                                        "/api/published-worlds/**"
                                )
                                .authenticated()
                                .requestMatchers("/api/admin/**", "/api/chat/**").authenticated()
                                .requestMatchers("/api/**")
                                .permitAll()
                                .anyRequest()
                                .permitAll()
                )
                .formLogin(form ->
                        form
                                .loginProcessingUrl("/api/auth/login")
                                .usernameParameter("loginId")
                                .passwordParameter("password")
                                .successHandler((request, response, authentication) ->
                                        response.setStatus(204)
                                )
                                .failureHandler((request, response, exception) ->
                                        response.sendError(401, "아이디 또는 비밀번호가 올바르지 않습니다.")
                                )
                                .permitAll()
                )
                .logout(logout ->
                        logout
                                .logoutUrl("/api/auth/logout")
                                .logoutSuccessHandler((request, response, authentication) ->
                                        response.setStatus(204)
                                )
                );

        return http.build();
    }
}
