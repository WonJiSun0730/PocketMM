package com.example.projectmm.domain.relationship;

public record RelationshipDelta(
        int affinityChange,
        int trustChange,
        int tensionChange
) {

    public static RelationshipDelta none() {
        return new RelationshipDelta(
                0,
                0,
                0
        );
    }
}