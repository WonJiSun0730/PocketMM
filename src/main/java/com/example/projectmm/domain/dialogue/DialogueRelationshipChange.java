package com.example.projectmm.domain.dialogue;

import com.example.projectmm.domain.relationship.RelationshipDelta;

public record DialogueRelationshipChange(
        Long targetCharacterId,
        RelationshipDelta delta
) {

    public static DialogueRelationshipChange none() {
        return new DialogueRelationshipChange(
                null,
                RelationshipDelta.none()
        );
    }

    public static DialogueRelationshipChange invalid() {
        return new DialogueRelationshipChange(
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
}