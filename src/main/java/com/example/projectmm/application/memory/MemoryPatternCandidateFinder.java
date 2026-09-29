package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.memory.MemoryAccessStateRepository;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MemoryPatternCandidateFinder {

    private final MemoryRepository memoryRepository;
    private final MemoryAccessStateRepository accessStates;
    private final MemoryProvenanceAccessPolicy provenanceAccess;
    private static final int MIN_RELEVANCE_OVERLAP = 2;

    public List<Memory> findRelated(
            Memory memory
    ) {
        if (memory == null
                || memory.getCharacter() == null
                || memory.getCharacter().getId() == null) {
            return List.of();
        }

        List<Memory> candidates = memoryRepository
                .findByCharacterIdOrderByRememberedAtDesc(
                        memory.getCharacter().getId()
                )
                .stream()
                .filter(candidate ->
                        candidate != memory
                )
                // 다른 방과 접근이 제한된 기억을 압축 원문으로 우회 노출하지 않는다.
                .filter(candidate -> sameConversation(memory, candidate))
                .filter(candidate ->
                        MemoryRelevance.overlap(
                                memory.getSummary(),
                                candidate.getSummary()
                        ) >= MIN_RELEVANCE_OVERLAP
                )
                .toList();
        var ids = candidates.stream().map(Memory::getId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return candidates;
        var restricted = new HashSet<>(accessStates.findMemoryIdsWithDifferentAccess(ids, MemoryAccessLevel.EXPLICIT));
        provenanceAccess.restrictions(ids).forEach((id, level) -> {
            if (level != MemoryAccessLevel.EXPLICIT) restricted.add(id);
        });
        return candidates.stream().filter(candidate -> !restricted.contains(candidate.getId())).toList();
    }

    private boolean sameConversation(Memory source, Memory candidate) {
        if (source.getConversation() == null || candidate.getConversation() == null) {
            return source.getConversation() == candidate.getConversation();
        }
        return source.getConversation() == candidate.getConversation()
                || source.getConversation().getId() != null
                && source.getConversation().getId().equals(candidate.getConversation().getId());
    }
}
