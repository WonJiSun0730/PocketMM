package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface MemoryCompressionEvidenceRepository extends JpaRepository<MemoryCompressionEvidence, Long> {
    List<MemoryCompressionEvidence> findByMemoryIdAndTargetLevel(Long memoryId, MemoryDetailLevel targetLevel);

    boolean existsByMemoryIdAndEvidenceMemoryId(Long memoryId, Long evidenceMemoryId);

    interface AccessEdge {
        Long getSourceId();
        Long getEvidenceId();
        Long getSourceCharacterId();
        Long getEvidenceCharacterId();
        Long getSourceConversationId();
        Long getEvidenceConversationId();
    }

    // 과거 단계의 근거도 현재 요약에 남아 있으므로 targetLevel로 제한하지 않는다.
    @Query("select e.memory.id as sourceId, e.evidenceMemory.id as evidenceId, "
            + "e.memory.character.id as sourceCharacterId, e.evidenceMemory.character.id as evidenceCharacterId, "
            + "sc.id as sourceConversationId, ec.id as evidenceConversationId "
            + "from MemoryCompressionEvidence e left join e.memory.conversation sc "
            + "left join e.evidenceMemory.conversation ec where e.memory.id in :ids")
    List<AccessEdge> findAccessEdges(@Param("ids") Collection<Long> ids);

    @Query("""
        select e
        from MemoryCompressionEvidence e
        join fetch e.evidenceMemory em
        where e.memory.id = :memoryId
        order by e.id desc
        """)
    List<MemoryCompressionEvidence> findForReactivation(
            @Param("memoryId") Long memoryId
    );
}
