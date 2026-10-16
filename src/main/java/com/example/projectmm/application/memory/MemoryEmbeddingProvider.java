package com.example.projectmm.application.memory;

import java.util.List;

public interface MemoryEmbeddingProvider {

    default String cacheNamespace() { return "legacy"; }

    List<Double> embed(
            String text
    );
}