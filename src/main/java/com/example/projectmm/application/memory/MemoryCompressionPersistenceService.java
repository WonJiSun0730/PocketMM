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
    private final MemoryAccessStateRepository accessStates;
    private final MemoryProvenanceAccessPolicy provenanceAccess;

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
        // 외부 압축 호출이 끝난 뒤 새 쓰기 transaction에서 다시 검증한다.
        // 이 검증 이후의 동시 access 변경까지 직렬화하는 잠금은 도입하지 않는다.
        var checked = new java.util.ArrayList<Long>(ids);
        checked.add(memoryId);
        if (!accessStates.findMemoryIdsWithDifferentAccess(checked, MemoryAccessLevel.EXPLICIT).isEmpty()
                || provenanceAccess.restrictions(checked).values().stream()
                    .anyMatch(level -> level != MemoryAccessLevel.EXPLICIT)) return false;
        if (evidence.stream().anyMatch(item -> !item.getCharacter().getId().equals(memory.getCharacter().getId())
                || !java.util.Objects.equals(item.getConversation() == null ? null : item.getConversation().getId(),
                        memory.getConversation() == null ? null : memory.getConversation().getId()))) return false;
        boolean directCycle = evidence.stream()
                .anyMatch(item ->
                        provenance.existsByMemoryIdAndEvidenceMemoryId(
                                item.getId(),
                                memoryId
                        )
                );

        if (directCycle) return false;
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
