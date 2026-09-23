package com.example.projectmm.domain.state;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomStateDefinitionRepository extends JpaRepository<CustomStateDefinition, Long> {
    List<CustomStateDefinition> findByPlayerProfileIdOrderByIdAsc(Long playerProfileId);
}
