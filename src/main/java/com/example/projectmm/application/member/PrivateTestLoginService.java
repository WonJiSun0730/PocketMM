package com.example.projectmm.application.member;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;

/** One account and one destination; never accepts a client-supplied email address. */
@Service
@ConditionalOnProperty(name = "app.private-test.enabled", havingValue = "true")
public class PrivateTestLoginService {
    public static final String VERIFIED = "PRIVATE_TEST_VERIFIED";
    public static final String ACCOUNT = "devseed";
    public static final String RECIPIENT = "wlwns13@gmail.com";
    private static final String PENDING = "PRIVATE_TEST_PENDING";
    private final MemberUserDetailsService users;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mail;
    private final String sender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private long nextSendAt;

    @org.springframework.beans.factory.annotation.Autowired
    public PrivateTestLoginService(MemberUserDetailsService users, PasswordEncoder encoder,
            ObjectProvider<JavaMailSender> mail, @Value("${spring.mail.username:}") String sender) {
        this(users, encoder, mail, sender, Clock.systemUTC());
    }

    PrivateTestLoginService(MemberUserDetailsService users, PasswordEncoder encoder,
            ObjectProvider<JavaMailSender> mail, String sender, Clock clock) {
        this.users = users; this.encoder = encoder; this.mail = mail;
        this.sender = sender; this.clock = clock;
    }

    public synchronized void send(String loginId, String password, HttpSession session) {
        if (!ACCOUNT.equals(loginId) || password == null) throw invalidCredentials();
        UserDetails user;
        try { user = users.loadUserByUsername(ACCOUNT); }
        catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) { throw invalidCredentials(); }
        if (!encoder.matches(password, user.getPassword()) || !user.isEnabled()
                || !user.isAccountNonLocked() || !user.isAccountNonExpired() || !user.isCredentialsNonExpired()) {
            throw invalidCredentials();
        }
        long now = clock.millis();
        if (now < nextSendAt) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "인증번호 재발송은 2분 후에 가능합니다.");
        JavaMailSender mailSender = mail.getIfAvailable();
        if (mailSender == null || sender.isBlank()) throw mailUnavailable();
        String code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        byte[] salt = new byte[32]; random.nextBytes(salt);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender); message.setTo(RECIPIENT);
        message.setSubject("[PocketMM] 로그인 인증번호");
        message.setText("로그인 인증번호: " + code + "\n5분 이내에 입력해주세요.");
        try { mailSender.send(message); }
        catch (org.springframework.mail.MailException e) { throw mailUnavailable(); }
        session.removeAttribute(VERIFIED);
        session.setAttribute(PENDING, new Pending(salt, digest(salt, code), now + 300_000));
        nextSendAt = clock.millis() + 120_000;
    }

    public synchronized UserDetails verify(String code, HttpSession session) {
        Object value = session == null ? null : session.getAttribute(PENDING);
        if (!(value instanceof Pending pending) || clock.millis() >= pending.expiresAt) {
            if (session != null) session.removeAttribute(PENDING);
            throw invalidCode();
        }
        pending.attempts++;
        boolean matches = code != null && code.matches("[0-9]{6}")
                && MessageDigest.isEqual(pending.hash, digest(pending.salt, code));
        if (!matches) {
            if (pending.attempts >= 5) session.removeAttribute(PENDING);
            throw invalidCode();
        }
        session.removeAttribute(PENDING);
        UserDetails user = users.loadUserByUsername(ACCOUNT);
        if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) throw invalidCredentials();
        nextSendAt = 0;
        return user;
    }

    private static byte[] digest(byte[] salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt); return digest.digest(code.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
    private static ResponseStatusException invalidCode() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증번호가 올바르지 않거나 만료되었습니다.");
    }
    private static ResponseStatusException mailUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "인증번호를 발송하지 못했습니다. 메일 설정을 확인해주세요.");
    }
    private static class Pending implements java.io.Serializable {
        final byte[] salt, hash; final long expiresAt; int attempts;
        Pending(byte[] salt, byte[] hash, long expiresAt) {
            this.salt = salt; this.hash = hash; this.expiresAt = expiresAt;
        }
    }
}
