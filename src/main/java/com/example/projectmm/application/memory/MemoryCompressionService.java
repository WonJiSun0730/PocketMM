package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryCompressionResult;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemoryCompressionService {

    private final MemoryDecayPolicy memoryDecayPolicy;
    private final MemoryCompressor memoryCompressor;

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

        String compressedSummary =
                memoryCompressor.compress(
                        memory,
                        targetLevel
                );

        memory.compress(compressedSummary);

        return MemoryCompressionResult.compressed(
                compressedSummary
        );
    }

}