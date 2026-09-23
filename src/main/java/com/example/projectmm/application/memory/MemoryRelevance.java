package com.example.projectmm.application.memory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Low-cost lexical ranking, not semantic/vector retrieval. */
final class MemoryRelevance {
    private MemoryRelevance() {}

    static int overlap(String query, String memory) {
        Set<String> grams = grams(query);
        grams.retainAll(grams(memory));
        return grams.size();
    }

    private static Set<String> grams(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) return result;
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            for (int i = 0; i + 1 < token.length(); i++) result.add(token.substring(i, i + 2));
        }
        return result;
    }
}
