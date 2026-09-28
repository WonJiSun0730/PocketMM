package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Component
@Profile("!gemini")
public class RuleBasedMemoryCompressor
        implements MemoryCompressor {

    @Override
    public MemoryCompressorOutput compress(
            Memory memory,
            MemoryDetailLevel targetLevel,
            List<Memory> relatedMemories
    ) {
        // This fallback does not consume related evidence; never claim candidate IDs as provenance.
        return new MemoryCompressorOutput(memory.getSummary(), List.of());
    }
}
