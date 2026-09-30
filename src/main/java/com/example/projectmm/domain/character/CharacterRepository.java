package com.example.projectmm.domain.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    boolean existsByName(String name);

    List<Character> findAllByOwnerIdOrderByIdDesc(Long ownerId);

    Optional<Character> findByIdAndOwnerId(Long id, Long ownerId);
}
