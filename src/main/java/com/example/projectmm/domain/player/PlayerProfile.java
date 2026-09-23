package com.example.projectmm.domain.player;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "player_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 로그인 도입 전 로컬 공용 목록의 10개 제한을 DB에서도 지킨다.
    @Column(nullable = false, unique = true)
    private int slot;
    @Column(nullable = false, length = 40)
    private String name;
    @Column(nullable = false, length = 500)
    private String description;
    @Lob @Column(columnDefinition = "MEDIUMTEXT")
    private String avatar;

    public PlayerProfile(int slot, String name, String description, String avatar) {
        this.slot = slot;
        update(name, description, avatar);
    }

    public void update(String name, String description, String avatar) {
        this.name = name;
        this.description = description;
        this.avatar = avatar;
    }
}
