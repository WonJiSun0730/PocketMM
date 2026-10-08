package com.example.projectmm.presentation.member;

import com.example.projectmm.application.member.MemberSignupService;
import com.example.projectmm.application.member.SignupRequest;
import com.example.projectmm.domain.member.Member;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @org.springframework.beans.factory.annotation.Value("${app.private-test.enabled:false}")
    private boolean privateTest;

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("registrationEnabled", !privateTest, "emailVerificationRequired", privateTest,
                "maskedEmail", privateTest ? "wl***@gmail.com" : "");
    }

    private final MemberSignupService memberSignupService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@Valid @RequestBody SignupRequest request) {
        Member member = memberSignupService.signup(request);

        return Map.of(
                "id", member.getId(),
                "loginId", member.getLoginId(),
                "name", member.getName(),
                "email", member.getEmail(),
                "role", member.getRole().name()
        );
    }

    @GetMapping("/check-login-id")
    public Map<String, Boolean> checkLoginId(@RequestParam String loginId) {
        return Map.of("available", memberSignupService.isLoginIdAvailable(loginId));
    }

    @GetMapping("/check-email")
    public Map<String, Boolean> checkEmail(@RequestParam String email) {
        return Map.of("available", memberSignupService.isEmailAvailable(email));
    }
    @GetMapping("/me")
    public CurrentMemberResponse me(Authentication authentication) {
        Member member = memberSignupService.findByLoginId(authentication.getName());

        return new CurrentMemberResponse(
                member.getId(),
                member.getLoginId(),
                member.getName(),
                member.getEmail(),
                member.getRole().name()
        );
    }
}

