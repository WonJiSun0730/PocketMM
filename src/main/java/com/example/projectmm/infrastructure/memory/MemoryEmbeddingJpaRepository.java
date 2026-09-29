package com.example.projectmm.infrastructure.memory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryEmbeddingJpaRepository
        extends JpaRepository<MemoryEmbeddingEntity, Long> {
}