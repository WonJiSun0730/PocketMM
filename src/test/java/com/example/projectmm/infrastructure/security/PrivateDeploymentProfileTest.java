package com.example.projectmm.infrastructure.security;

import com.example.projectmm.domain.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "PROD_DB_URL=jdbc:h2:mem:privateprod;DB_CLOSE_DELAY=-1", "PROD_DB_USERNAME=sa", "PROD_DB_PASSWORD=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("prod")
class PrivateDeploymentProfileTest {
    @Autowired Environment environment;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwords;

    @Test void productionDefaultsToPrivateLoginBehindHttpsAndCreatesRequestedAccount() {
        assertThat(environment.getProperty("app.private-test.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        var account = members.findByLoginId("devseed").orElseThrow();
        assertThat(passwords.matches("devseed-password", account.getPassword())).isTrue();
        assertThat(members.count()).isEqualTo(1);
    }
}
