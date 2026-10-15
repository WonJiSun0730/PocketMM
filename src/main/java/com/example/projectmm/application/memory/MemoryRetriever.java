package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;

import java.util.List;

public interface MemoryRetriever {

    /** Pocket prompt selection must not pad results with unrelated strong memories. */
    default List<EffectiveMemory> retrieveRelevant(String query, List<EffectiveMemory> candidates, int limit) {
        if (candidates == null || query == null || query.isBlank() || limit <= 0) return List.of();
        var relevance = new MemoryRetrievalRanker();
        String focused = PocketMemoryQuery.focus(query,candidates);
        var eligible = candidates.stream().filter(memory -> relevance.isRelevant(focused, memory)
                || PocketMemoryQuery.requestedFactScore(focused,memory)>0).toList();
        var ranked = retrieve(focused, eligible, eligible.size()).stream()
                .sorted(java.util.Comparator.<EffectiveMemory>comparingInt(memory -> PocketMemoryQuery.requestedFactScore(focused,memory)).reversed())
                .toList();
        return PocketMemoryDeduplicator.select(ranked, limit);
    }

    List<EffectiveMemory> retrieve(
            String query,
            List<EffectiveMemory> candidates,
            int limit
    );
}