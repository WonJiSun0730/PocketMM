package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MemoryPatternCandidateFinder {

    private final MemoryRepository memoryRepository;
    private static final int MIN_RELEVANCE_OVERLAP = 2;

    public List<Memory> findRelated(
            Memory memory
    ) {
        if (memory == null
                || memory.getCharacter() == null
                || memory.getCharacter().getId() == null) {
            return List.of();
        }

        return memoryRepository
                .findByCharacterIdOrderByRememberedAtDesc(
                        memory.getCharacter().getId()
                )
                .stream()
                .filter(candidate ->
                        candidate != memory
                )
                .filter(candidate ->
                        MemoryRelevance.overlap(
                                memory.getSummary(),
                                candidate.getSummary()
                        ) >= MIN_RELEVANCE_OVERLAP
                )
                .toList();
    }
}