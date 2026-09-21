package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryRepository
        extends JpaRepository<Memory, Long> {

    List<Memory> findByCharacterIdOrderByRememberedAtDesc(
            Long characterId
    );
}