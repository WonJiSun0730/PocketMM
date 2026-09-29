package com.example.projectmm.application.memory;

import java.util.List;

public class UnavailableMemoryEmbeddingProvider
        implements MemoryEmbeddingProvider {

    @Override
    public List<Double> embed(
            String text
    ) {
        return List.of();
    }
}