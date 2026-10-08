package com.example.projectmm.domain.chat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "world_room_intros")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRoomIntro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private WorldConversation room;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int sortOrder;

    private LocalDateTime initialSimulatedAt;

    @Column(length = 200)
    private String initialLocation;

    @ElementCollection
    @CollectionTable(name = "world_room_intro_conditions", joinColumns = @JoinColumn(name = "intro_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "condition_text", nullable = false, length = 300)
    private List<String> initialConditions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "world_room_intro_inventory", joinColumns = @JoinColumn(name = "intro_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "item_text", nullable = false, length = 300)
    private List<String> initialInventory = new ArrayList<>();

    public WorldRoomIntro(WorldConversation room, String title, String content, int sortOrder) {
        this(room, title, content, sortOrder, null, null, List.of(), List.of());
    }

    public WorldRoomIntro(WorldConversation room, String title, String content, int sortOrder, LocalDateTime initialSimulatedAt, String initialLocation, List<String> initialConditions, List<String> initialInventory) {
        if (room == null) throw new IllegalArgumentException("room is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        this.room = room;
        this.title = title.trim();
        this.content = content.trim();
        this.sortOrder = sortOrder;
        updateInitialScene(initialSimulatedAt, initialLocation, initialConditions, initialInventory);
    }

    public void update(String title, String content, int sortOrder) {
        update(title, content, sortOrder, initialSimulatedAt, initialLocation, initialConditions, initialInventory);
    }

    public void update(String title, String content, int sortOrder, LocalDateTime initialSimulatedAt, String initialLocation, List<String> initialConditions, List<String> initialInventory) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content is required");
        this.title = title.trim();
        this.content = content.trim();
        this.sortOrder = sortOrder;
        updateInitialScene(initialSimulatedAt, initialLocation, initialConditions, initialInventory);
    }

    private void updateInitialScene(LocalDateTime simulatedAt, String location, List<String> conditions, List<String> inventory) {
        this.initialSimulatedAt = simulatedAt;
        this.initialLocation = normalizeOptional(location, 200);
        this.initialConditions.clear();
        this.initialConditions.addAll(normalizeList(conditions));
        this.initialInventory.clear();
        this.initialInventory.addAll(normalizeList(inventory));
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException("Intro scene value is too long.");
        return normalized;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim)
                .peek(value -> { if (value.length() > 300) throw new IllegalArgumentException("Intro scene item is too long."); })
                .distinct().limit(50).toList();
    }
}
