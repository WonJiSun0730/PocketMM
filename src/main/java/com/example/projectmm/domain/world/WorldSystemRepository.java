package com.example.projectmm.domain.world;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorldSystemRepository extends JpaRepository<WorldSystem, Long> {
    List<WorldSystem> findByWorldIdOrderBySortOrderAsc(Long worldId);
    void deleteByWorldId(Long worldId);
}
