package com.example.projectmm.domain.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterExampleRepository
        extends JpaRepository<CharacterExample, Long> {

    List<CharacterExample>
    findByCharacterIdOrderBySortOrderAsc(Long characterId);

    void deleteByCharacterId(Long characterId);
}