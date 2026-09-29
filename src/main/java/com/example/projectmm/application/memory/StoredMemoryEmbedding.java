package com.example.projectmm.application.memory;

import java.util.List;

public record StoredMemoryEmbedding(
        String documentFingerprint,
        List<Double> embedding
) {
    public StoredMemoryEmbedding {
        if (documentFingerprint == null
                || documentFingerprint.isBlank()) {
            throw new IllegalArgumentException(
                    "Document fingerprint is required"
            );
        }

        if (embedding == null
                || embedding.isEmpty()) {
            throw new IllegalArgumentException(
                    "Embedding is required"
            );
        }

        documentFingerprint =
                documentFingerprint.trim();

        embedding =
                List.copyOf(embedding);
    }
}