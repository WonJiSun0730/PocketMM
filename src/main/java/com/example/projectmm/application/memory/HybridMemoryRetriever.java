package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HybridMemoryRetriever
        implements MemoryRetriever {

    private static final int VECTOR_WEIGHT = 2;
    private static final int FALLBACK_WEIGHT = 1;

    private final MemoryEmbeddingProvider embeddingProvider;
    private final CachedMemoryEmbeddingService memoryEmbeddingService;
    private final MemoryRetrievalRanker fallbackRanker;

    public HybridMemoryRetriever(
            MemoryEmbeddingProvider embeddingProvider,
            CachedMemoryEmbeddingService memoryEmbeddingService,
            MemoryRetrievalRanker fallbackRanker
    ) {
        this.embeddingProvider = embeddingProvider;
        this.memoryEmbeddingService = memoryEmbeddingService;
        this.fallbackRanker = fallbackRanker;
    }

    @Override
    public List<EffectiveMemory> retrieve(
            String query,
            List<EffectiveMemory> candidates,
            int limit
    ) {
        if (candidates == null
                || candidates.isEmpty()
                || limit <= 0) {
            return List.of();
        }

        List<EffectiveMemory> fallback =
                fallbackRanker.rank(
                        query,
                        candidates,
                        candidates.size()
                );

        if (query == null
                || query.isBlank()) {
            return limit(
                    fallback,
                    limit
            );
        }

        List<Double> queryEmbedding =
                embeddingProvider.embed(query);

        if (queryEmbedding == null
                || queryEmbedding.isEmpty()) {
            return limit(
                    fallback,
                    limit
            );
        }

        List<EffectiveMemory> vectorRanked =
                vectorRank(
                        queryEmbedding,
                        candidates
                );

        if (vectorRanked.isEmpty()) {
            return limit(
                    fallback,
                    limit
            );
        }

        return fuse(
                vectorRanked,
                fallback,
                limit
        );
    }

    private List<EffectiveMemory> vectorRank(
            List<Double> queryEmbedding,
            List<EffectiveMemory> candidates
    ) {
        return candidates.stream()
                .filter(memory ->
                        memory.semanticMemory() != null
                )
                .map(memory ->
                        new ScoredMemory(
                                memory,
                                EmbeddingSimilarity.cosine(
                                        queryEmbedding,
                                        memoryEmbeddingService.embeddingFor(
                                                memory.memoryId(),
                                                memory.semanticMemory()
                                        )
                                )
                        )
                )
                .filter(scored ->
                        scored.score() > 0.0
                )
                .sorted(
                        Comparator
                                .comparingDouble(
                                        ScoredMemory::score
                                )
                                .reversed()
                                .thenComparing(
                                        scored ->
                                                scored.memory()
                                                        .effectiveStrength(),
                                        Comparator.reverseOrder()
                                )
                                .thenComparing(
                                        scored ->
                                                scored.memory()
                                                        .rememberedAt(),
                                        Comparator.reverseOrder()
                                )
                )
                .map(ScoredMemory::memory)
                .toList();
    }

    private List<EffectiveMemory> fuse(
            List<EffectiveMemory> vectorRanked,
            List<EffectiveMemory> fallbackRanked,
            int limit
    ) {
        Map<Long, Double> scores =
                new HashMap<>();

        addRankScores(
                scores,
                vectorRanked,
                VECTOR_WEIGHT
        );

        addRankScores(
                scores,
                fallbackRanked,
                FALLBACK_WEIGHT
        );

        Map<Long, EffectiveMemory> memories =
                new LinkedHashMap<>();

        for (EffectiveMemory memory : fallbackRanked) {
            memories.put(
                    memory.memoryId(),
                    memory
            );
        }

        for (EffectiveMemory memory : vectorRanked) {
            memories.putIfAbsent(
                    memory.memoryId(),
                    memory
            );
        }

        List<EffectiveMemory> result =
                new ArrayList<>(
                        memories.values()
                );

        result.sort(
                Comparator
                        .<EffectiveMemory>comparingDouble(
                                memory ->
                                        scores.getOrDefault(
                                                memory.memoryId(),
                                                0.0
                                        )
                        )
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt(
                                        EffectiveMemory::effectiveStrength
                                ).reversed()
                        )
                        .thenComparing(
                                EffectiveMemory::rememberedAt,
                                Comparator.reverseOrder()
                        )
        );

        return limit(
                result,
                limit
        );
    }

    private void addRankScores(
            Map<Long, Double> scores,
            List<EffectiveMemory> ranked,
            int weight
    ) {
        for (int i = 0; i < ranked.size(); i++) {
            EffectiveMemory memory =
                    ranked.get(i);

            double score =
                    (double) weight
                            / (i + 1);

            scores.merge(
                    memory.memoryId(),
                    score,
                    Double::sum
            );
        }
    }

    private List<EffectiveMemory> limit(
            List<EffectiveMemory> memories,
            int limit
    ) {
        return memories.stream()
                .limit(limit)
                .toList();
    }

    private record ScoredMemory(
            EffectiveMemory memory,
            double score
    ) {
    }
}