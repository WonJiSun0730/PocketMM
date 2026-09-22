package com.example.projectmm.domain.character;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    boolean existsByName(String name);
}