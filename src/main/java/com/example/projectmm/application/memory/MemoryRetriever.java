package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;

import java.util.List;

public interface MemoryRetriever {

    List<EffectiveMemory> retrieve(
            String query,
            List<EffectiveMemory> candidates,
            int limit
    );
}