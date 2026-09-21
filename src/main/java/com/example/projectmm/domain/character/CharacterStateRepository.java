package com.example.projectmm.domain.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CharacterStateRepository
        extends JpaRepository<CharacterState, Long> {

    Optional<CharacterState> findByCharacterId(Long characterId);
}