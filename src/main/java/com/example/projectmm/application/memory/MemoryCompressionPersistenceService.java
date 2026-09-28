package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class MemoryCompressionPersistenceService {
    private final MemoryRepository memories;
    private final MemoryCompressionEvidenceRepository provenance;

    // Recall can run in a read-only transaction. Commit both changes in one independent write transaction.
    // No AI call is made here; callers synchronize their in-memory view only after this commit succeeds.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(Long memoryId, MemoryDetailLevel expectedLevel, String expectedSummary,
                         MemoryCompressorOutput output) {
        if (memoryId == null || output == null || output.summary() == null
                || output.summary().isBlank() || output.summary().length() > 500) return false;
        var memory = memories.lockForCompression(memoryId).orElse(null);
        if (memory == null || memory.getDetailLevel() != expectedLevel
                || !memory.getSummary().equals(expectedSummary) || !expectedLevel.canCompressFurther()) return false;
        var target = expectedLevel.next();
        List<Long> ids = target == MemoryDetailLevel.SUMMARY ? List.of() : output.evidenceMemoryIds();
        int minimum = target == MemoryDetailLevel.SCHEMA ? 2 : target == MemoryDetailLevel.PATTERN ? 1 : 0;
        if (ids.size() < minimum || ids.contains(memoryId)) return false;
        var evidence = memories.findAllById(ids);
        // Reject the entire result rather than silently dropping missing evidence.
        if (evidence.size() != ids.size()) return false;
        var existing = new HashSet<Long>();
        if (target != MemoryDetailLevel.SUMMARY) {
            provenance.findByMemoryIdAndTargetLevel(memoryId, target)
                    .forEach(row -> existing.add(row.getEvidenceMemory().getId()));
        }
        var rows = evidence.stream().filter(item -> !existing.contains(item.getId()))
                .map(item -> new MemoryCompressionEvidence(memory, target, item)).toList();
        memory.compress(output.summary());
        provenance.saveAllAndFlush(rows);
        memories.flush();
        return true;
    }
}
