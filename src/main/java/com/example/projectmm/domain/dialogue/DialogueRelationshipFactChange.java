package com.example.projectmm.domain.dialogue;

public record DialogueRelationshipFactChange(
        Long targetCharacterId,
        String relation,
        String value
) {

    public static DialogueRelationshipFactChange none() {
        return new DialogueRelationshipFactChange(
                null,
                null,
                null
        );
    }

    public static DialogueRelationshipFactChange invalid() {
        return new DialogueRelationshipFactChange(
                null,
                null,
                null
        );
    }

    public boolean hasChange() {
        return targetCharacterId != null
                || (relation != null && !relation.isBlank())
                || (value != null && !value.isBlank());
    }
}