package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface MemoryAccessStateRepository
        extends JpaRepository<MemoryAccessState, Long> {

    Optional<MemoryAccessState> findByMemoryId(
            Long memoryId
    );

    // ID만 반환해 후보별 조회와 lazy association 초기화를 모두 피한다.
    @Query("select s.memory.id from MemoryAccessState s where s.memory.id in :ids and s.accessLevel <> :level")
    List<Long> findMemoryIdsWithDifferentAccess(@Param("ids") Collection<Long> ids,
                                              @Param("level") MemoryAccessLevel level);

    interface AccessEntry {
        Long getMemoryId();
        MemoryAccessLevel getAccessLevel();
    }

    @Query("select s.memory.id as memoryId, s.accessLevel as accessLevel from MemoryAccessState s where s.memory.id in :ids")
    List<AccessEntry> findAccessEntries(@Param("ids") Collection<Long> ids);
}
