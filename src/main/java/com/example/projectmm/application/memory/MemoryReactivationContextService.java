package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryReactivationContextService {

    private static final int MAX_EVIDENCE = 3;

    private final MemoryCompressionEvidenceRepository evidenceRepository;
    private final MemoryAccessStateRepository accessStateRepository;
    private final MemoryProvenanceAccessPolicy provenanceAccess;
    private final MemoryRepository memoryRepository;

    public List<Memory> accessibleEvidence(
            Long memoryId
    ) {
        if (memoryId == null) {
            return List.of();
        }

        return memoryRepository.findById(memoryId)
                .map(this::accessibleEvidence)
                .orElseGet(List::of);
    }

    public List<Memory> accessibleEvidence(
            Memory memory
    ) {
        if (memory == null
                || memory.getId() == null
                || !isCompressed(memory.getDetailLevel())) {
            return List.of();
        }

        List<Memory> evidence =
                evidenceRepository
                        .findForReactivation(memory.getId())
                        .stream()
                        .map(MemoryCompressionEvidence::getEvidenceMemory)
                        .distinct()
                        .toList();

        if (evidence.isEmpty()) {
            return List.of();
        }

        var restrictions =
                provenanceAccess.restrictions(
                        evidence.stream()
                                .map(Memory::getId)
                                .toList()
                );

        return evidence.stream()
                .filter(item ->
                        accessLevel(
                                item,
                                restrictions
                        ) == MemoryAccessLevel.EXPLICIT
                )
                .limit(MAX_EVIDENCE)
                .toList();
    }

    private boolean isCompressed(
            MemoryDetailLevel detailLevel
    ) {
        return detailLevel == MemoryDetailLevel.PATTERN
                || detailLevel == MemoryDetailLevel.SCHEMA;
    }

    private MemoryAccessLevel accessLevel(
            Memory memory,
            java.util.Map<Long, MemoryAccessLevel> restrictions
    ) {
        MemoryAccessLevel own =
                accessStateRepository
                        .findByMemoryId(memory.getId())
                        .map(state ->
                                state.getAccessLevel()
                        )
                        .orElse(
                                MemoryAccessLevel.EXPLICIT
                        );

        return MemoryProvenanceAccessPolicy.stricter(
                own,
                restrictions.getOrDefault(
                        memory.getId(),
                        MemoryAccessLevel.EXPLICIT
                )
        );
    }
}