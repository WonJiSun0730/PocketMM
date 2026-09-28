package com.example.projectmm.domain.memory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "memory_access_states",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = "memory_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryAccessState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "memory_id",
            nullable = false,
            unique = true
    )
    private Memory memory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryAccessLevel accessLevel;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public MemoryAccessState(
            Memory memory,
            MemoryAccessLevel accessLevel,
            LocalDateTime updatedAt
    ) {
        if (memory == null) {
            throw new IllegalArgumentException(
                    "memory must not be null"
            );
        }

        if (accessLevel == null) {
            throw new IllegalArgumentException(
                    "accessLevel must not be null"
            );
        }

        if (updatedAt == null) {
            throw new IllegalArgumentException(
                    "updatedAt must not be null"
            );
        }

        this.memory = memory;
        this.accessLevel = accessLevel;
        this.updatedAt = updatedAt;
    }

    public void changeAccessLevel(
            MemoryAccessLevel accessLevel,
            LocalDateTime updatedAt
    ) {
        if (accessLevel == null) {
            throw new IllegalArgumentException(
                    "accessLevel must not be null"
            );
        }

        if (updatedAt == null) {
            throw new IllegalArgumentException(
                    "updatedAt must not be null"
            );
        }

        this.accessLevel = accessLevel;
        this.updatedAt = updatedAt;
    }
}