package com.example.projectmm.domain.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(
        name = "member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_login_id", columnNames = "login_id"),
                @UniqueConstraint(name = "uk_member_email", columnNames = "email")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "login_id", nullable = false, length = 20)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private Member(
            String loginId,
            String password,
            String name,
            String email,
            Role role
    ) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public static Member createLocal(
            String loginId,
            String rawPassword,
            String name,
            String email,
            PasswordEncoder passwordEncoder
    ) {
        return new Member(
                loginId,
                passwordEncoder.encode(rawPassword),
                name,
                email,
                Role.USER
        );
    }
}
