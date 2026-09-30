package com.example.projectmm.domain.dialogue;

import com.example.projectmm.domain.relationship.RelationshipDelta;

public record DialogueRelationshipChange(
        Long targetCharacterId,
        RelationshipDelta delta,
        String evidence
) {

    public static DialogueRelationshipChange none() {
        return new DialogueRelationshipChange(
                null,
                RelationshipDelta.none(),
                ""
        );
    }

    public static DialogueRelationshipChange invalid() {
        return new DialogueRelationshipChange(
                null,
                null,
                null
        );
    }

    public boolean hasChange() {
        return delta != null
                && (
                delta.affinityChange() != 0
                        || delta.trustChange() != 0
                        || delta.tensionChange() != 0
        );
    }

    public boolean validFor(String userText) {
        if (!hasChange()) {
            return true;
        }

        if (evidence == null
                || evidence.isBlank()
                || userText == null
                || userText.isBlank()) {
            return false;
        }

        String normalizedEvidence =
                evidence.trim();

        return normalizedEvidence.length() <= 240
                && userText.contains(normalizedEvidence);
    }
}