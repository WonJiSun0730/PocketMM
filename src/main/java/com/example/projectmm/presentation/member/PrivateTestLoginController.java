package com.example.projectmm.presentation.member;

import com.example.projectmm.application.member.PrivateTestLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "app.private-test.enabled", havingValue = "true")
public class PrivateTestLoginController {
    private final PrivateTestLoginService service;
    public PrivateTestLoginController(PrivateTestLoginService service) { this.service = service; }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> login(@RequestParam String loginId, @RequestParam String password,
            HttpServletRequest request) {
        service.send(loginId, password, request.getSession());
        return Map.of("emailVerificationRequired", true, "expiresInSeconds", 300, "resendAfterSeconds", 120);
    }

    @PostMapping("/verify-login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@RequestBody Map<String, String> body, HttpServletRequest request,
            HttpServletResponse response) {
        var user = service.verify(body.get("code"), request.getSession(false));
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        request.getSession().setAttribute(PrivateTestLoginService.VERIFIED, true);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
