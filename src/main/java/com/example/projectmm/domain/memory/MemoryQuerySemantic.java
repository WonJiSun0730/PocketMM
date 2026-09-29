package com.example.projectmm.domain.memory;

public record MemoryQuerySemantic(
        SemanticMemory.Type type,
        String subject,
        String relation,
        String object,
        SemanticMemory.Polarity polarity,
        SemanticMemory.Temporality temporality
) {

    public MemoryQuerySemantic {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Query semantic type is required"
            );
        }

        subject = normalize(subject);
        relation = normalize(relation);
        object = normalize(object);
    }

    public static MemoryQuerySemantic intent(
            SemanticMemory.Type type,
            String subject,
            String relation
    ) {
        return new MemoryQuerySemantic(
                type,
                subject,
                relation,
                null,
                null,
                null
        );
    }

    private static String normalize(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}