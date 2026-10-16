package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.SemanticMemory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class CachedMemoryEmbeddingService {

    private final MemoryEmbeddingProvider embeddingProvider;
    private final MemoryEmbeddingStore embeddingStore;

    public CachedMemoryEmbeddingService(
            MemoryEmbeddingProvider embeddingProvider,
            MemoryEmbeddingStore embeddingStore
    ) {
        this.embeddingProvider =
                embeddingProvider;

        this.embeddingStore =
                embeddingStore;
    }

    public List<Double> embeddingFor(
            Long memoryId,
            SemanticMemory semanticMemory
    ) {
        if (memoryId == null
                || semanticMemory == null) {
            return List.of();
        }

        String document =
                SemanticMemoryDocument.from(
                        semanticMemory
                );

        if (document.isBlank()) {
            return List.of();
        }

        String fingerprint =
                fingerprint(embeddingProvider.cacheNamespace()+"\n"+document);

        var cached =
                embeddingStore.find(memoryId);

        if (cached.isPresent()
                && cached.get()
                .documentFingerprint()
                .equals(fingerprint)) {

            return cached.get()
                    .embedding();
        }

        List<Double> embedding =
                embeddingProvider.embed(
                        document
                );

        if (embedding == null
                || embedding.isEmpty()) {
            return List.of();
        }

        List<Double> immutable =
                List.copyOf(embedding);

        embeddingStore.save(
                memoryId,
                new StoredMemoryEmbedding(
                        fingerprint,
                        immutable
                )
        );

        return immutable;
    }

    private String fingerprint(
            String document
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    document.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    e
            );
        }
    }
}