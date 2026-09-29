package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryAccessStateRepository;
import com.example.projectmm.domain.memory.MemoryCompressionEvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** 저장된 access와 분리된 provenance 제한. 호출 간 캐시하지 않아 회복/차단을 다음 조회에 반영한다. */
@Service
@RequiredArgsConstructor
public class MemoryProvenanceAccessPolicy {
    private final MemoryCompressionEvidenceRepository provenance;
    private final MemoryAccessStateRepository accessStates;
    private static final int BATCH_SIZE = 256;

    public Map<Long, MemoryAccessLevel> restrictions(Collection<Long> roots) {
        var visited = new HashSet<Long>();
        var frontier = new HashSet<Long>();
        roots.stream().filter(Objects::nonNull).forEach(frontier::add);
        var edges = new ArrayList<MemoryCompressionEvidenceRepository.AccessEdge>();
        while (!frontier.isEmpty()) {
            var batch = new ArrayList<>(frontier);
            visited.addAll(frontier);
            frontier.clear();
            for (int i = 0; i < batch.size(); i += BATCH_SIZE) {
                var found = provenance.findAccessEdges(batch.subList(i, Math.min(i + BATCH_SIZE, batch.size())));
                edges.addAll(found);
                for (var edge : found) {
                    if (sameScope(edge) && !visited.contains(edge.getEvidenceId())) frontier.add(edge.getEvidenceId());
                }
            }
        }
        if (edges.isEmpty()) return Map.of(); // SUMMARY 및 provenance 없는 legacy는 제한을 추가하지 않는다.
        var own = new HashMap<Long, MemoryAccessLevel>();
        var ids = new ArrayList<>(visited);
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            accessStates.findAccessEntries(ids.subList(i, Math.min(i + BATCH_SIZE, ids.size())))
                    .forEach(entry -> own.put(entry.getMemoryId(), entry.getAccessLevel()));
        }
        var inherited = new HashMap<Long, MemoryAccessLevel>();
        // 제한은 단조롭게 증가한다. 기존 cycle이 있어도 종료하며 간접 근거의 제한이 전파된다.
        boolean changed;
        do {
            changed = false;
            for (var edge : edges) {
                var restriction = sameScope(edge)
                        ? stricter(own.getOrDefault(edge.getEvidenceId(), MemoryAccessLevel.EXPLICIT),
                            inherited.getOrDefault(edge.getEvidenceId(), MemoryAccessLevel.EXPLICIT))
                        : MemoryAccessLevel.BLOCKED;
                var previous = inherited.getOrDefault(edge.getSourceId(), MemoryAccessLevel.EXPLICIT);
                var next = stricter(previous, restriction);
                if (previous != next) {
                    inherited.put(edge.getSourceId(), next);
                    changed = true;
                }
            }
        } while (changed);
        return inherited;
    }

    private boolean sameScope(MemoryCompressionEvidenceRepository.AccessEdge edge) {
        return Objects.equals(edge.getSourceCharacterId(), edge.getEvidenceCharacterId())
                && Objects.equals(edge.getSourceConversationId(), edge.getEvidenceConversationId());
    }

    public static MemoryAccessLevel stricter(MemoryAccessLevel own, MemoryAccessLevel inherited) {
        return rank(own) >= rank(inherited) ? own : inherited;
    }

    private static int rank(MemoryAccessLevel level) {
        return switch (level) {
            case EXPLICIT -> 0;
            case IMPLICIT -> 1;
            case RESIDUAL -> 2;
            case BLOCKED -> 3;
        };
    }
}
