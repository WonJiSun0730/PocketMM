package com.example.projectmm.domain.world;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldSystemStateRepository extends JpaRepository<WorldSystemState, Long> {
    List<WorldSystemState> findBySystemIdOrderBySortOrderAsc(Long systemId);
    void deleteBySystemId(Long systemId);
    void deleteBySystemWorldId(Long worldId);
}
