package com.example.projectmm.infrastructure.seed;

import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.domain.member.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates only the requested test account on a fresh deployment database. */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.private-test.enabled", havingValue = "true")
public class PrivateTestAccountRunner implements CommandLineRunner {
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    public PrivateTestAccountRunner(MemberRepository members, PasswordEncoder encoder) {
        this.members = members; this.encoder = encoder;
    }
    @Override @Transactional
    public void run(String... args) {
        if (members.findByLoginId("devseed").isPresent()) return;
        var member = Member.createLocal("devseed", "devseed-password", "Dev Seed",
                "devseed@pocketmm.local", encoder);
        member.changeRole(Role.DEVELOPER);
        members.save(member);
    }
}
