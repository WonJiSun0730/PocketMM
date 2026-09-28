package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailLevel;

public interface MemoryCompressor {

    String compress(
            Memory memory,
            MemoryDetailLevel targetLevel
    );
}