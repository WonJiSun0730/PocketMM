package com.example.projectmm.domain.relationship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelationshipFactRepository
        extends JpaRepository<RelationshipFact, Long> {

    List<RelationshipFact>
    findByFromCharacterId(Long fromCharacterId);

    List<RelationshipFact>
    findByFromCharacterIdAndToCharacterId(
            Long fromCharacterId,
            Long toCharacterId
    );

    Optional<RelationshipFact>
    findByFromCharacterIdAndToCharacterIdAndRelation(
            Long fromCharacterId,
            Long toCharacterId,
            String relation
    );
}