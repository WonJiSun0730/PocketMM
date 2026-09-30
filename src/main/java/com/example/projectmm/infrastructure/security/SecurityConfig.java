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
            HttpSecurity http
    ) throws Exception {

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
                                        "/api/worlds/**"
                                )
                                .authenticated()
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
