package com.example.projectmm.domain.world;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorldSystemRepository extends JpaRepository<WorldSystem, Long> {
    @org.springframework.data.jpa.repository.Query("select s from WorldSystem s where s.world.id = :worldId and s.archived = false order by s.sortOrder asc")
    List<WorldSystem> findByWorldIdOrderBySortOrderAsc(@org.springframework.data.repository.query.Param("worldId") Long worldId);
    void deleteByWorldId(Long worldId);
}
