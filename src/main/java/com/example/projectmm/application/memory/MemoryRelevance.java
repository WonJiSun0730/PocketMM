package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.MemoryFact;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Low-cost lexical + structured-memory ranking.
 * Semantic/vector retrieval을 대체하지 않고,
 * 명확한 query intent와 MemoryFact 종류가 일치할 때만 보조 점수를 준다.
 */
final class MemoryRelevance {

    private static final int FACT_KIND_MATCH_BONUS = 5;

    private MemoryRelevance() {
    }

    static int overlap(String query, String memory) {
        Set<String> queryGrams =
                grams(query);

        Set<String> memoryGrams =
                grams(memory);

        removeRecallNoise(queryGrams);

        queryGrams.retainAll(memoryGrams);

        return queryGrams.size();
    }

    static int selectionOverlap(String query, String memory) {
        var queryGrams = grams(query);
        var memoryGrams = grams(memory);
        // Shared grammatical endings alone are not evidence of a shared topic.
        var grammar = Set.of("한다", "했다", "하다", "이다", "있다", "없다", "라고", "하고", "하는", "하며", "해요", "어요", "아요", "니다", "기억");
        queryGrams.removeAll(grammar);
        queryGrams.retainAll(memoryGrams);
        return queryGrams.size();
    }

    private static void removeRecallNoise(
            Set<String> grams
    ) {
        grams.remove("기억");
    }

    static int structuredBonus(
            String query,
            MemoryFact fact
    ) {
        if (query == null
                || query.isBlank()
                || fact == null
                || fact.getKind() == null) {
            return 0;
        }

        return switch (fact.getKind()) {
            case PREFERENCE ->
                    containsAny(
                            query,
                            "취향",
                            "좋아",
                            "싫어",
                            "선호"
                    )
                            ? FACT_KIND_MATCH_BONUS
                            : 0;

            case IDENTITY ->
                    containsAny(
                            query,
                            "누구",
                            "이름",
                            "정체",
                            "신분"
                    )
                            ? FACT_KIND_MATCH_BONUS
                            : 0;

            case RELATIONSHIP ->
                    containsAny(
                            query,
                            "관계",
                            "사이",
                            "누구랑",
                            "누구와"
                    )
                            ? FACT_KIND_MATCH_BONUS
                            : 0;

            case CONSTRAINT ->
                    containsAny(
                            query,
                            "제약",
                            "조건",
                            "못",
                            "안 돼",
                            "안돼",
                            "금지"
                    )
                            ? FACT_KIND_MATCH_BONUS
                            : 0;

            case PLAN ->
                    containsAny(
                            query,
                            "계획",
                            "예정",
                            "하려",
                            "할 거",
                            "할거"
                    )
                            ? FACT_KIND_MATCH_BONUS
                            : 0;

            case REPORTED_EVENT ->
                    containsAny(
                            query,
                            "무슨 일",
                            "무슨일",
                            "있었",
                            "했었",
                            "일어났"
                    )
                            ? FACT_KIND_MATCH_BONUS
                            : 0;
        };
    }

    private static boolean containsAny(
            String query,
            String... keywords
    ) {
        String normalized =
                query.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (normalized.contains(
                    keyword.toLowerCase(Locale.ROOT)
            )) {
                return true;
            }
        }

        return false;
    }

    private static Set<String> grams(String text) {
        Set<String> result = new HashSet<>();

        if (text == null) {
            return result;
        }

        for (String token :
                text.toLowerCase(Locale.ROOT)
                        .split("[^\\p{L}\\p{N}]+")) {

            for (int i = 0;
                 i + 1 < token.length();
                 i++) {

                result.add(
                        token.substring(i, i + 2)
                );
            }
        }

        return result;
    }
}