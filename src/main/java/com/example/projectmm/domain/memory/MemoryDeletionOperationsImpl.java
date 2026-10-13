package com.example.projectmm.domain.memory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared cleanup for timeline, Pocket, and room deletion, in the caller's transaction. */
@RequiredArgsConstructor
public class MemoryDeletionOperationsImpl implements MemoryDeletionOperations {
    private final EntityManager em;

    @Override
    @Transactional
    public void deleteWithDependencies(Collection<Long> memoryIds) {
        if (memoryIds.isEmpty()) return;
        em.flush();
        var ids = new LinkedHashSet<>(memoryIds);
        var frontier = List.copyOf(ids);
        // A compressed summary cannot retain facts learned from a deleted timeline.
        // Follow reverse provenance transitively; the visited set also handles legacy cycles.
        while (!frontier.isEmpty()) {
            var dependent = em.createQuery(
                    "select distinct e.memory.id from MemoryCompressionEvidence e where e.evidenceMemory.id in :ids",
                    Long.class).setParameter("ids", frontier).getResultList();
            frontier = dependent.stream().filter(ids::add).toList();
        }
        var targets = List.copyOf(ids);
        var evidence = em.createQuery(
                "select e from MemoryCompressionEvidence e where e.memory.id in :ids or e.evidenceMemory.id in :ids",
                MemoryCompressionEvidence.class).setParameter("ids", targets).getResultList();
        evidence.forEach(em::remove);
        em.createQuery("select s from MemoryAccessState s where s.memory.id in :ids", MemoryAccessState.class)
                .setParameter("ids", targets).getResultList().forEach(em::remove);
        em.createQuery("select d from ProtectedMemoryDetail d where d.memory.id in :ids", ProtectedMemoryDetail.class)
                .setParameter("ids", targets).getResultList().forEach(em::remove);
        em.createQuery("select e from MemoryEmbeddingEntity e where e.memoryId in :ids",
                com.example.projectmm.infrastructure.memory.MemoryEmbeddingEntity.class)
                .setParameter("ids", targets).getResultList().forEach(em::remove);
        // Flush child removals before scheduling parent removals; keep the session managed.
        em.flush();
        em.createQuery("select m from Memory m where m.id in :ids", Memory.class)
                .setParameter("ids", targets).getResultList().forEach(em::remove);
        em.flush();
    }
}
