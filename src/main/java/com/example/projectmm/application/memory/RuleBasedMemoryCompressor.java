package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedMemoryCompressor
        implements MemoryCompressor {

    @Override
    public String compress(Memory memory) {
        return memory.getSummary();
    }
}