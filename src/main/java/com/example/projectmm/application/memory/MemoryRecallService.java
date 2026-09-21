package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryRecallService {

    private final MemoryRepository memoryRepository;
    private final MemoryDecayPolicy memoryDecayPolicy;
    private final MemoryCompressionService memoryCompressionService;

    public List<EffectiveMemory> recall(
            Long characterId,
            LocalDateTime currentTime,
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        List<Memory> memories =
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        );

        return memories.stream()
                .map(memory ->
                        prepareMemory(
                                memory,
                                currentTime
                        )
                )
                .filter(memory ->
                        memory.effectiveStrength() > 0
                )
                .sorted(
                        Comparator.comparingInt(
                                EffectiveMemory::effectiveStrength
                        ).reversed()
                )
                .limit(limit)
                .toList();
    }

    private EffectiveMemory prepareMemory(
            Memory memory,
            LocalDateTime currentTime
    ) {
        MemoryDecayResult decay =
                memoryDecayPolicy.calculate(
                        memory,
                        currentTime
                );

        if (decay.effectiveStrength() > 0
                && decay.compressionRequired()) {

            memoryCompressionService.compressIfNeeded(
                    memory,
                    currentTime
            );
        }

        return new EffectiveMemory(
                memory.getId(),
                memory.getSummary(),
                memory.getImportance(),
                decay.effectiveStrength(),
                memory.getRememberedAt(),
                decay.compressionRequired()
        );
    }
}