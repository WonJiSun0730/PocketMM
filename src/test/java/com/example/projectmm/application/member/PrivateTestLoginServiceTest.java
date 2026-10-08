package com.example.projectmm.application.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateTestLoginServiceTest {
    MemberUserDetailsService users;
    JavaMailSender mail;
    PrivateTestLoginService service;
    Clock clock;
    MockHttpSession session;
    @BeforeEach void setup() {
        users = mock(MemberUserDetailsService.class); mail = mock(JavaMailSender.class);
        var encoder = new BCryptPasswordEncoder();
        when(users.loadUserByUsername("devseed")).thenReturn(User.withUsername("devseed")
                .password(encoder.encode("devseed-password")).roles("DEVELOPER").build());
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        clock = mock(Clock.class); when(clock.millis()).thenReturn(1_000_000L);
        service = new PrivateTestLoginService(users, encoder, provider, "sender@example.com", clock);
        session = new MockHttpSession();
    }
    String sentCode() {
        var captured = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captured.capture());
        assertThat(captured.getValue().getTo()).containsExactly("wlwns13@gmail.com");
        return captured.getValue().getText().replaceAll("(?s).*: ([0-9]{6}).*", "$1");
    }
    @Test void wrongCredentialsNeverSend() {
        assertThatThrownBy(() -> service.send("other", "devseed-password", session)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.send("devseed", "wrong", session)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mail); assertThat(session.getAttribute(PrivateTestLoginService.VERIFIED)).isNull();
    }
    @Test void sixDigitsSessionBoundOneUse() {
        service.send("devseed", "devseed-password", session); String code = sentCode();
        assertThat(code).matches("[0-9]{6}");
        assertThatThrownBy(() -> service.verify(code, new MockHttpSession())).isInstanceOf(ResponseStatusException.class);
        assertThat(service.verify(code, session).getUsername()).isEqualTo("devseed");
        assertThatThrownBy(() -> service.verify(code, session)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void fiveFailuresInvalidateCode() {
        service.send("devseed", "devseed-password", session); String code = sentCode();
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.verify("invalid", session)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.verify(code, session)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void expiresAndResendAcrossSessions() {
        service.send("devseed", "devseed-password", session); String code = sentCode();
        assertThatThrownBy(() -> service.send("devseed", "devseed-password", new MockHttpSession()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
        when(clock.millis()).thenReturn(1_300_000L);
        assertThatThrownBy(() -> service.verify(code, session)).isInstanceOf(ResponseStatusException.class);
        service.send("devseed", "devseed-password", new MockHttpSession());
        verify(mail, times(2)).send(any(SimpleMailMessage.class));
    }
    @Test void mailFailureCannotAuthenticateOrConsumeCooldown() {
        doThrow(new MailSendException("failed")).when(mail).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.send("devseed", "devseed-password", session))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThatThrownBy(() -> service.verify("000000", session)).isInstanceOf(ResponseStatusException.class);
        doNothing().when(mail).send(any(SimpleMailMessage.class));
        service.send("devseed", "devseed-password", session);
    }
}
