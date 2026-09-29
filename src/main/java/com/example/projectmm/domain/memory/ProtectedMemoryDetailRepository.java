package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProtectedMemoryDetailRepository
        extends JpaRepository<
        ProtectedMemoryDetail,
        Long
        > {

    List<ProtectedMemoryDetail>
    findByMemoryIdOrderByIdAsc(
            Long memoryId
    );
}