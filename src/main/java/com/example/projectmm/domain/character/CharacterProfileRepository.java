package com.example.projectmm.domain.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CharacterProfileRepository
        extends JpaRepository<CharacterProfile, Long> {

    Optional<CharacterProfile> findByCharacterId(Long characterId);
}