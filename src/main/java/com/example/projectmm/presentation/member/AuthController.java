package com.example.projectmm.presentation.member;

import com.example.projectmm.application.member.MemberSignupService;
import com.example.projectmm.application.member.SignupRequest;
import com.example.projectmm.domain.member.Member;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

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
}
