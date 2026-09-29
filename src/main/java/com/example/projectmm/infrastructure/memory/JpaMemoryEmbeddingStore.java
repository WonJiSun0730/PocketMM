package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.MemoryEmbeddingStore;
import com.example.projectmm.application.memory.StoredMemoryEmbedding;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

public class JpaMemoryEmbeddingStore
        implements MemoryEmbeddingStore {

    private static final TypeReference<List<Double>>
            EMBEDDING_TYPE =
            new TypeReference<>() {
            };

    private final MemoryEmbeddingJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaMemoryEmbeddingStore(
            MemoryEmbeddingJpaRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredMemoryEmbedding> find(
            Long memoryId
    ) {
        if (memoryId == null) {
            return Optional.empty();
        }

        return repository.findById(memoryId)
                .flatMap(this::toStoredEmbedding);
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

        String serializedEmbedding =
                serialize(
                        embedding.embedding()
                );

        MemoryEmbeddingEntity entity =
                repository.findById(memoryId)
                        .orElseGet(
                                () ->
                                        new MemoryEmbeddingEntity(
                                                memoryId,
                                                embedding.documentFingerprint(),
                                                serializedEmbedding
                                        )
                        );

        if (entity.getMemoryId() != null
                && repository.existsById(memoryId)) {
            entity.replace(
                    embedding.documentFingerprint(),
                    serializedEmbedding
            );
        }

        repository.save(entity);
    }

    private Optional<StoredMemoryEmbedding>
    toStoredEmbedding(
            MemoryEmbeddingEntity entity
    ) {
        try {
            List<Double> embedding =
                    objectMapper.readValue(
                            entity.getEmbedding(),
                            EMBEDDING_TYPE
                    );

            if (embedding == null
                    || embedding.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(
                    new StoredMemoryEmbedding(
                            entity.getDocumentFingerprint(),
                            embedding
                    )
            );
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String serialize(
            List<Double> embedding
    ) {
        try {
            return objectMapper.writeValueAsString(
                    embedding
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to serialize memory embedding",
                    e
            );
        }
    }
}