package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
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

    public MemoryCompressionResult compressIfNeeded(
            Memory memory,
            LocalDateTime currentTime
    ) {
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

        String compressedSummary =
                memoryCompressor.compress(
                        memory,
                        targetLevel,
                        relatedMemories
                );

        if (compressedSummary == null || compressedSummary.isBlank() || compressedSummary.length() > 500) {
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
