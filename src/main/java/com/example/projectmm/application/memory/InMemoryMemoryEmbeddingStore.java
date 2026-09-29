package com.example.projectmm.application.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMemoryEmbeddingStore
        implements MemoryEmbeddingStore {

    private final Map<Long, StoredMemoryEmbedding> embeddings =
            new ConcurrentHashMap<>();

    @Override
    public Optional<StoredMemoryEmbedding> find(
            Long memoryId
    ) {
        if (memoryId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                embeddings.get(memoryId)
        );
    }

    @Override
    public void save(
            Long memoryId,
            StoredMemoryEmbedding embedding
    ) {
        if (memoryId == null
                || embedding == null) {
            return;
        }

        embeddings.put(
                memoryId,
                embedding
        );
    }
}