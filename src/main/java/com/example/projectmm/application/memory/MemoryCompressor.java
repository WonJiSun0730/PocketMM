package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailLevel;

import java.util.List;

public interface MemoryCompressor {

    // null은 안전하게 압축할 수 없음을 뜻한다. 호출자는 현재 내용과 단계를 유지한다.
    MemoryCompressorOutput compress(
            Memory memory,
            MemoryDetailLevel targetLevel,
            List<Memory> relatedMemories
    );
}
