package com.example.projectmm.domain.world;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldLoreEntryRepository extends JpaRepository<WorldLoreEntry, Long> {
    List<WorldLoreEntry> findByWorldIdAndCategoryOrderBySortOrderAsc(Long worldId, WorldLoreCategory category);
    void deleteByWorldIdAndCategory(Long worldId, WorldLoreCategory category);
}
