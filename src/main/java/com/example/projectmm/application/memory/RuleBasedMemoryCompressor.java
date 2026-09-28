package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedMemoryCompressor
        implements MemoryCompressor {

    @Override
    public String compress(
            Memory memory,
            MemoryDetailLevel targetLevel
    ) {
        return memory.getSummary();
    }
}