package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryCompressionEvidenceRepository extends JpaRepository<MemoryCompressionEvidence, Long> {
    List<MemoryCompressionEvidence> findByMemoryIdAndTargetLevel(Long memoryId, MemoryDetailLevel targetLevel);

    boolean existsByMemoryIdAndEvidenceMemoryId(Long memoryId, Long evidenceMemoryId);
}
