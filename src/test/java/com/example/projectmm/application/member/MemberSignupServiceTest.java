package com.example.projectmm.application.member;

import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.member.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberSignupServiceTest {

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private MemberSignupService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new MemberSignupService(memberRepository, passwordEncoder);
    }

    @Test
    void signupCreatesUserWithEncodedPassword() {
        SignupRequest request = new SignupRequest(
                "pocket01",
                "password123",
                "password123",
                "Pocket",
                "POCKET@example.com"
        );

        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Member saved = service.signup(request);

        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getEmail()).isEqualTo("pocket@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void signupRejectsDuplicateLoginId() {
        when(memberRepository.existsByLoginId("pocket01")).thenReturn(true);

        SignupRequest request = new SignupRequest(
                "pocket01",
                "password123",
                "password123",
                "Pocket",
                "pocket@example.com"
        );

        assertThatThrownBy(() -> service.signup(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 사용 중인 아이디입니다.");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(memberRepository.existsByEmail("pocket@example.com")).thenReturn(true);

        SignupRequest request = new SignupRequest(
                "pocket01",
                "password123",
                "password123",
                "Pocket",
                "POCKET@example.com"
        );

        assertThatThrownBy(() -> service.signup(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 가입된 이메일입니다.");

        verify(memberRepository, never()).save(any());
    }
}
