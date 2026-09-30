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

    // AI 호출을 transaction으로 감싸지 않고 context에 필요한 상대 이름까지 조회한다.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "toCharacter")
    List<Relationship> findByFromCharacterId(
            Long fromCharacterId
    );
}
