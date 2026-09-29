package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.SemanticMemory;

final class SemanticMemoryDocument {

    private SemanticMemoryDocument() {
    }

    static String from(
            SemanticMemory memory
    ) {
        if (memory == null) {
            return "";
        }

        return String.join(
                " ",
                "TYPE_" + memory.getType().name(),
                "SUBJECT_" + memory.getSubject(),
                "RELATION_" + memory.getRelation(),
                "OBJECT_" + normalizeObject(
                        memory.getObject()
                ),
                "POLARITY_" + memory.getPolarity().name(),
                "TEMPORALITY_" + memory.getTemporality().name(),
                "CERTAINTY_" + memory.getCertainty().name()
        );
    }

    private static String normalizeObject(
            String object
    ) {
        if (object.startsWith("LITERAL:")) {
            return "LITERAL";
        }

        return object;
    }
}