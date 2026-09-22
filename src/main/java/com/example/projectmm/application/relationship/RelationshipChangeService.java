package com.example.projectmm.application.relationship;

import com.example.projectmm.domain.relationship.Relationship;
import com.example.projectmm.domain.relationship.RelationshipDelta;
import com.example.projectmm.domain.relationship.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RelationshipChangeService {

    private final RelationshipRepository relationshipRepository;

    public void apply(
            Long fromCharacterId,
            Long toCharacterId,
            RelationshipDelta delta
    ) {
        if (toCharacterId == null || delta == null) {
            return;
        }

        if (delta.affinityChange() == 0
                && delta.trustChange() == 0
                && delta.tensionChange() == 0) {
            return;
        }

        Relationship relationship =
                relationshipRepository
                        .findByFromCharacterIdAndToCharacterId(
                                fromCharacterId,
                                toCharacterId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Relationship not found. fromCharacterId="
                                                + fromCharacterId
                                                + ", toCharacterId="
                                                + toCharacterId
                                )
                        );

        relationship.apply(delta);
    }
}