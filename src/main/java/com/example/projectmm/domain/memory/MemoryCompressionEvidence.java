package com.example.projectmm.domain.memory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A related evidence reference for one successful target stage; never copies raw memory text. */
@Entity
@Table(name = "memory_compression_evidence", uniqueConstraints = @UniqueConstraint(
        columnNames = {"memory_id", "target_level", "evidence_memory_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryCompressionEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memory_id", nullable = false)
    private Memory memory;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false)
    private MemoryDetailLevel targetLevel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_memory_id", nullable = false)
    private Memory evidenceMemory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryDetailLevel evidenceLevel;

    public MemoryCompressionEvidence(Memory memory, MemoryDetailLevel targetLevel, Memory evidenceMemory) {
        if (memory == null || evidenceMemory == null || memory.getId() == null || evidenceMemory.getId() == null) {
            throw new IllegalArgumentException("provenance requires persisted memories");
        }
        if (memory.getId().equals(evidenceMemory.getId())) {
            throw new IllegalArgumentException("related evidence must not reference itself");
        }
        if (targetLevel != MemoryDetailLevel.PATTERN && targetLevel != MemoryDetailLevel.SCHEMA) {
            throw new IllegalArgumentException("related evidence is only recorded for PATTERN or SCHEMA");
        }
        this.memory = memory;
        this.targetLevel = targetLevel;
        this.evidenceMemory = evidenceMemory;
        this.evidenceLevel = evidenceMemory.getDetailLevel();
    }
}
