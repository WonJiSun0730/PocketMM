package com.example.projectmm.application.memory;

import java.util.List;

public interface MemoryEmbeddingProvider {

    List<Double> embed(
            String text
    );
}