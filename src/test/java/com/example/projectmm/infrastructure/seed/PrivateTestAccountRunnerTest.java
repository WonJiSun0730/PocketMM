package com.example.projectmm.infrastructure.seed;

import com.example.projectmm.domain.member.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PrivateTestAccountRunnerTest {
    @Test void createsOnlyRequestedAccountWhenAbsent() {
        var repo = mock(MemberRepository.class); var encoder = new BCryptPasswordEncoder();
        when(repo.findByLoginId("devseed")).thenReturn(Optional.empty());
        new PrivateTestAccountRunner(repo, encoder).run();
        var captured = org.mockito.ArgumentCaptor.forClass(Member.class);
        verify(repo).save(captured.capture());
        assertThat(captured.getValue().getLoginId()).isEqualTo("devseed");
        assertThat(captured.getValue().getRole()).isEqualTo(Role.DEVELOPER);
        assertThat(encoder.matches("devseed-password", captured.getValue().getPassword())).isTrue();
    }
    @Test void existingAccountIsNeverOverwritten() {
        var repo = mock(MemberRepository.class); var encoder = new BCryptPasswordEncoder();
        var member = Member.createLocal("devseed", "changed-password", "Existing", "old@example.com", encoder);
        when(repo.findByLoginId("devseed")).thenReturn(Optional.of(member));
        new PrivateTestAccountRunner(repo, encoder).run();
        verify(repo, never()).save(any());
        assertThat(encoder.matches("changed-password", member.getPassword())).isTrue();
        assertThat(member.getRole()).isEqualTo(Role.USER);
    }
}
