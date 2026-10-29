package com.example.projectmm.domain.chat;

import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "world_room_player_states", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_room_player_state_session",
        columnNames = "session_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomPlayerState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private WorldRoomSession session;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private String location;

    @ElementCollection
    @CollectionTable(name = "world_room_player_conditions", joinColumns = @JoinColumn(name = "player_state_id"))
    @OrderColumn(name = "sort_order")
    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "condition_text", nullable = false, columnDefinition = "LONGTEXT")
    private List<String> conditions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "world_room_player_inventory", joinColumns = @JoinColumn(name = "player_state_id"))
    @OrderColumn(name = "sort_order")
    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "item_text", nullable = false, columnDefinition = "LONGTEXT")
    private List<String> inventory = new ArrayList<>();

    public WorldRoomPlayerState(WorldRoomSession session) {
        if (session == null) throw new IllegalArgumentException("World room session is required.");
        this.session = session;
    }

    public void update(String location, List<String> conditions, List<String> inventory) {
        this.location = normalizeOptional(location);
        this.conditions.clear();
        this.conditions.addAll(normalizeList(conditions));
        this.inventory.clear();
        this.inventory.addAll(normalizeList(inventory));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("Location must be 200 characters or fewer.");
        return normalized;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .peek(value -> {
                    if (value.length() > 300) throw new IllegalArgumentException("State item must be 300 characters or fewer.");
                })
                .distinct()
                .limit(50)
                .toList();
    }
}
