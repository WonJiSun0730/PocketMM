package com.example.projectmm.domain.world;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRepository extends JpaRepository<World, Long> {
    org.springframework.data.domain.Page<World> findByPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(org.springframework.data.domain.Pageable pageable);
    List<World> findAllByOwnerIdOrderByIdDesc(Long ownerId);
    Optional<World> findByIdAndOwnerId(Long id, Long ownerId);
}
