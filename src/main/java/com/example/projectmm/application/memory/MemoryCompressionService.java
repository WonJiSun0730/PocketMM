package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryAccessStateRepository;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryCompressionResult;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryCompressionService {
    private static final int MIN_PATTERN_RELATED_MEMORIES = 1;
    private static final int MIN_SCHEMA_RELATED_MEMORIES = 2;

    private final MemoryDecayPolicy memoryDecayPolicy;
    private final MemoryCompressor memoryCompressor;
    private final MemoryPatternCandidateFinder candidateFinder;
    private final MemoryCompressionPersistenceService persistence;
    private final MemoryAccessStateRepository accessStates;

    public MemoryCompressionResult compressIfNeeded(
            Memory memory,
            LocalDateTime currentTime
    ) {
        // Recall 외 직접 호출도 포함해 접근 제한 기억의 원문을 compressor에 전달하지 않는다.
        if (memory.getId() != null && accessStates.findByMemoryId(memory.getId())
                .map(state -> state.getAccessLevel() != MemoryAccessLevel.EXPLICIT).orElse(false)) {
            return MemoryCompressionResult.skipped(memory.getSummary());
        }
        if (!memory.getDetailLevel()
                .canCompressFurther()) {

            return MemoryCompressionResult.skipped(
                    memory.getSummary()
            );
        }

        MemoryDecayResult decay =
                memoryDecayPolicy.calculate(
                        memory,
                        currentTime
                );

        if (!decay.compressionRequired()) {
            return MemoryCompressionResult.skipped(
                    memory.getSummary()
            );
        }

        MemoryDetailLevel targetLevel =
                memory.getDetailLevel().next();

        List<Memory> relatedMemories =
                relatedMemories(
                        memory,
                        targetLevel
                );

        if (!hasEnoughEvidence(
                targetLevel,
                relatedMemories
        )) {
            return MemoryCompressionResult.skipped(
                    memory.getSummary()
            );
        }

        MemoryCompressorOutput output =
                memoryCompressor.compress(
                        memory,
                        targetLevel,
                        relatedMemories
                );

        String compressedSummary = output == null ? null : output.summary();
        if (compressedSummary == null || compressedSummary.isBlank() || compressedSummary.length() > 500) {
            return MemoryCompressionResult.skipped(memory.getSummary());
        }

        int minimum = targetLevel == MemoryDetailLevel.SCHEMA ? 2 : targetLevel == MemoryDetailLevel.PATTERN ? 1 : 0;
        if (output.evidenceMemoryIds().size() < minimum
                || !persistence.apply(memory.getId(), memory.getDetailLevel(), memory.getSummary(), output)) {
            return MemoryCompressionResult.skipped(memory.getSummary());
        }
        memory.compress(compressedSummary);

        return MemoryCompressionResult.compressed(
                compressedSummary
        );
    }

    private List<Memory> relatedMemories(
            Memory memory,
            MemoryDetailLevel targetLevel
    ) {
        if (targetLevel == MemoryDetailLevel.SUMMARY) {
            return List.of();
        }

        return candidateFinder.findRelated(memory);
    }

    private boolean hasEnoughEvidence(
            MemoryDetailLevel targetLevel,
            List<Memory> relatedMemories
    ) {
        return switch (targetLevel) {
            case DETAILED, SUMMARY -> true;

            case PATTERN ->
                    relatedMemories.size()
                            >= MIN_PATTERN_RELATED_MEMORIES;

            case SCHEMA ->
                    relatedMemories.size()
                            >= MIN_SCHEMA_RELATED_MEMORIES;
        };
    }
}
