package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "memory_embeddings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryEmbeddingEntity {

    @Id
    @Column(name = "memory_id")
    private Long memoryId;

    @Column(
            name = "document_fingerprint",
            nullable = false,
            length = 64
    )
    private String documentFingerprint;

    @Lob
    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "embedding",
            nullable = false,
            columnDefinition = "LONGTEXT")
    private String embedding;

    public MemoryEmbeddingEntity(
            Long memoryId,
            String documentFingerprint,
            String embedding
    ) {
        if (memoryId == null) {
            throw new IllegalArgumentException(
                    "Memory id is required"
            );
        }

        if (documentFingerprint == null
                || documentFingerprint.isBlank()) {
            throw new IllegalArgumentException(
                    "Document fingerprint is required"
            );
        }

        if (embedding == null
                || embedding.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding is required"
            );
        }

        this.memoryId = memoryId;
        this.documentFingerprint =
                documentFingerprint.trim();
        this.embedding = embedding;
    }

    public void replace(
            String documentFingerprint,
            String embedding
    ) {
        if (documentFingerprint == null
                || documentFingerprint.isBlank()) {
            throw new IllegalArgumentException(
                    "Document fingerprint is required"
            );
        }

        if (embedding == null
                || embedding.isBlank()) {
            throw new IllegalArgumentException(
                    "Embedding is required"
            );
        }

        this.documentFingerprint =
                documentFingerprint.trim();
        this.embedding = embedding;
    }
}