package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemoryAccessStateRepository
        extends JpaRepository<MemoryAccessState, Long> {

    Optional<MemoryAccessState> findByMemoryId(
            Long memoryId
    );
}