package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleBasedMemoryCompressor
        implements MemoryCompressor {

    @Override
    public String compress(
            Memory memory,
            MemoryDetailLevel targetLevel,
            List<Memory> relatedMemories
    ) {
        return memory.getSummary();
    }
}