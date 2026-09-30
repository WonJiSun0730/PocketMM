package com.example.projectmm.domain.dialogue;

public record DialogueRelationshipFactChange(
        Long targetCharacterId,
        String relation,
        String value,
        String evidence
) {

    public static DialogueRelationshipFactChange none() {
        return new DialogueRelationshipFactChange(
                null,
                null,
                null,
                ""
        );
    }

    public static DialogueRelationshipFactChange invalid() {
        return new DialogueRelationshipFactChange(
                null,
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

    public boolean validFor(String userText) {
        if (targetCharacterId == null
                || relation == null
                || relation.isBlank()
                || value == null
                || value.isBlank()
                || evidence == null
                || evidence.isBlank()
                || userText == null
                || userText.isBlank()) {
            return false;
        }

        String normalizedEvidence = evidence.trim();

        return normalizedEvidence.length() <= 240
                && userText.contains(normalizedEvidence);
    }
}