package com.example.projectmm.domain.relationship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelationshipRepository
        extends JpaRepository<Relationship, Long> {

    Optional<Relationship>
    findByFromCharacterIdAndToCharacterId(
            Long fromCharacterId,
            Long toCharacterId
    );

    List<Relationship> findByFromCharacterId(
            Long fromCharacterId
    );
}