package com.example.projectmm.domain.world;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRepository extends JpaRepository<World, Long> {
    List<World> findAllByOwnerIdOrderByIdDesc(Long ownerId);
    Optional<World> findByIdAndOwnerId(Long id, Long ownerId);
}
