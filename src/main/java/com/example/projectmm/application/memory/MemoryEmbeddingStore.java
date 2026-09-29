package com.example.projectmm.application.memory;

import java.util.Optional;

public interface MemoryEmbeddingStore {

    Optional<StoredMemoryEmbedding> find(
            Long memoryId
    );

    void save(
            Long memoryId,
            StoredMemoryEmbedding embedding
    );
}