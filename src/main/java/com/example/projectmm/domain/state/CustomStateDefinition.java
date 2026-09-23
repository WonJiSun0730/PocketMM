package com.example.projectmm.domain.state;

import com.example.projectmm.domain.player.PlayerProfile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "custom_state_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomStateDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_profile_id", nullable = false)
    private PlayerProfile playerProfile;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double minValue;

    @Column(nullable = false)
    private double maxValue;

    @Column(nullable = false)
    private double defaultValue;

    private String unit;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    public CustomStateDefinition(PlayerProfile playerProfile, String name,
                                 double minValue, double maxValue, double defaultValue,
                                 String unit, String description) {
        if (playerProfile == null) {
            throw new IllegalArgumentException("Player profile is required.");
        }
        this.playerProfile = playerProfile;
        update(name, minValue, maxValue, defaultValue, unit, description);
    }

    public void update(String name, double minValue, double maxValue, double defaultValue,
                       String unit, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Custom state name is required.");
        }
        if (!Double.isFinite(minValue) || !Double.isFinite(maxValue) || !Double.isFinite(defaultValue)
                || minValue > defaultValue || defaultValue > maxValue) {
            throw new IllegalArgumentException("Custom state requires finite minValue <= defaultValue <= maxValue.");
        }
        // 모든 검증을 통과한 뒤 정의를 바꾼다. 방별 현재값은 이 엔티티에 저장하지 않는다.
        this.name = name;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
        this.unit = unit;
        this.description = description;
    }
}
