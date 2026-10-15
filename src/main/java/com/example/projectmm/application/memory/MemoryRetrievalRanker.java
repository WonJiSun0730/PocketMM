package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;

import java.util.Comparator;
import java.util.List;

public class MemoryRetrievalRanker
        implements MemoryRetriever {

    @Override
    public List<EffectiveMemory> retrieve(
            String query,
            List<EffectiveMemory> candidates,
            int limit
    ) {
        return rank(
                query,
                candidates,
                limit
        );
    }

    public List<EffectiveMemory> rank(
            String query,
            List<EffectiveMemory> candidates,
            int limit
    ) {
        if (candidates == null
                || candidates.isEmpty()
                || limit <= 0) {
            return List.of();
        }

        return candidates.stream()
                .sorted(
                        Comparator
                                .<EffectiveMemory>comparingInt(
                                        memory ->
                                                relevance(
                                                        query,
                                                        memory
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
                )
                .limit(limit)
                .toList();
    }

    public boolean isRelevant(String query, EffectiveMemory memory) {
        if (query == null || query.isBlank() || memory.accessLevel() == com.example.projectmm.domain.memory.MemoryAccessLevel.BLOCKED) return false;
        String evidence = memory.evidenceQuote() == null || memory.evidenceQuote().isBlank()
                ? memory.summary() : memory.evidenceQuote();
        return MemoryRelevance.selectionOverlap(query, evidence) >= (query.strip().length() <= 3 ? 1 : 2)
                || MemoryRelevance.structuredBonus(query, memory.fact()) > 0;
    }

    private int relevance(
            String query,
            EffectiveMemory memory
    ) {
        int lexicalScore =
                Math.max(
                        MemoryRelevance.overlap(
                                query,
                                memory.summary()
                        ),
                        MemoryRelevance.overlap(
                                query,
                                memory.evidenceQuote()
                        )
                );

        int structuredBonus =
                MemoryRelevance.structuredBonus(
                        query,
                        memory.fact()
                );

        if (lexicalScore > 0) {
            return applyAccessStrength(
                    lexicalScore * 10,
                    memory.accessLevel()
            );
        }

        return applyAccessStrength(
                structuredBonus,
                memory.accessLevel()
        );
    }

    private int applyAccessStrength(
            int score,
            com.example.projectmm.domain.memory.MemoryAccessLevel accessLevel
    ) {
        return switch (accessLevel) {
            case EXPLICIT -> score;
            case IMPLICIT -> score * 60 / 100;
            case RESIDUAL -> score * 30 / 100;
            case BLOCKED -> 0;
        };
    }
}