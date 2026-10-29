package com.example.projectmm.domain.memory;

import com.example.projectmm.infrastructure.security.EncryptedTextConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SemanticMemory {

    public enum Type {
        IDENTITY,
        PREFERENCE,
        RELATIONSHIP,
        CONSTRAINT,
        PLAN,
        EVENT,
        EMOTION
    }

    public enum Polarity {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }

    public enum Temporality {
        PAST,
        PRESENT,
        FUTURE,
        TIMELESS
    }

    public enum Certainty {
        CERTAIN,
        UNCERTAIN,
        REPORTED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_type", length = 32)
    private Type type;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "semantic_subject", columnDefinition = "LONGTEXT")
    private String subject;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "semantic_relation", columnDefinition = "LONGTEXT")
    private String relation;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(name = "semantic_object", columnDefinition = "LONGTEXT")
    private String object;

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_polarity", length = 16)
    private Polarity polarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_temporality", length = 16)
    private Temporality temporality;

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_certainty", length = 16)
    private Certainty certainty;

    public SemanticMemory(
            Type type,
            String subject,
            String relation,
            String object,
            Polarity polarity,
            Temporality temporality,
            Certainty certainty
    ) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Semantic memory type is required"
            );
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException(
                    "Semantic memory subject is required"
            );
        }

        if (relation == null || relation.isBlank()) {
            throw new IllegalArgumentException(
                    "Semantic memory relation is required"
            );
        }

        if (object == null || object.isBlank()) {
            throw new IllegalArgumentException(
                    "Semantic memory object is required"
            );
        }

        if (polarity == null) {
            throw new IllegalArgumentException(
                    "Semantic memory polarity is required"
            );
        }

        if (temporality == null) {
            throw new IllegalArgumentException(
                    "Semantic memory temporality is required"
            );
        }

        if (certainty == null) {
            throw new IllegalArgumentException(
                    "Semantic memory certainty is required"
            );
        }

        this.type = type;
        this.subject = subject.trim();
        this.relation = relation.trim();
        this.object = object.trim();
        this.polarity = polarity;
        this.temporality = temporality;
        this.certainty = certainty;
    }
}