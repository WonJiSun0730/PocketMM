package com.example.projectmm.application.member;

import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberSignupService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signup(SignupRequest request) {
        String loginId = request.loginId().trim();
        String email = request.email().trim().toLowerCase();
        String name = request.name().trim();

        if (memberRepository.existsByLoginId(loginId)) {
            throw new IllegalStateException("이미 사용 중인 아이디입니다.");
        }

        if (memberRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }

        return memberRepository.save(
                Member.createLocal(
                        loginId,
                        request.password(),
                        name,
                        email,
                        passwordEncoder
                )
        );
    }

    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        return loginId != null
                && !loginId.isBlank()
                && !memberRepository.existsByLoginId(loginId.trim());
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return email != null
                && !email.isBlank()
                && !memberRepository.existsByEmail(email.trim().toLowerCase());
    }
    @Transactional(readOnly = true)
    public Member findByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }
}

