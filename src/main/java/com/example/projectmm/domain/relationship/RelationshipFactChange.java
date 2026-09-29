package com.example.projectmm.domain.relationship;

public record RelationshipFactChange(
        Long targetCharacterId,
        String relation,
        String value
) {

    public static RelationshipFactChange none() {
        return new RelationshipFactChange(
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