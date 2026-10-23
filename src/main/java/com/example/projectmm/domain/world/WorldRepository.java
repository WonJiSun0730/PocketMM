package com.example.projectmm.domain.world;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRepository extends JpaRepository<World, Long> {
    org.springframework.data.domain.Page<World> findByPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(org.springframework.data.domain.Pageable pageable);
    List<World> findAllByOwnerIdOrderByIdDesc(Long ownerId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from World w where w.id = :id")
    Optional<World> findForLoreUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<World> findByIdAndOwnerId(Long id, Long ownerId);
}
