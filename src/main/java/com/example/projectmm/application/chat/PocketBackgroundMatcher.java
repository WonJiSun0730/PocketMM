package com.example.projectmm.application.chat;

import java.text.Normalizer;
import java.util.*;

/** Conservative place matching: share a scene type, never just an incidental word. */
public final class PocketBackgroundMatcher {
    private PocketBackgroundMatcher() {}
    private static final Map<String, List<String>> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("counseling", List.of("상담실", "상담센터", "counseling room", "counselling room", "counseling center"));
        TYPES.put("bedroom", List.of("침실", "bedroom"));
        TYPES.put("kitchen", List.of("주방", "부엌", "kitchen"));
        TYPES.put("lounge", List.of("휴게실", "라운지", "lounge", "break room"));
        TYPES.put("office", List.of("사무실", "집무실", "office"));
        TYPES.put("cafe", List.of("카페", "커피숍", "cafe", "coffee shop"));
        TYPES.put("park", List.of("공원", "park"));
        TYPES.put("forest", List.of("숲", "forest", "woods"));
        TYPES.put("street", List.of("거리", "도로", "street", "road"));
        TYPES.put("home", List.of("자택", "집", "home", "house"));
    }
    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    public static Set<String> types(String value) {
        String text = normalize(value);
        if (text.matches(".*(가는 길|향하는 길|돌아오는 길|돌아가는 길|오는 길|on the way|way home).*")) return Set.of("route");
        Set<String> found = new LinkedHashSet<>();
        TYPES.forEach((type, words) -> {
            if (words.stream().anyMatch(word -> word.matches("[a-z ]+")
                    ? (" " + text + " ").contains(" " + word + " ") : text.contains(word))) found.add(type);
        });
        // A home bedroom is a bedroom, not an interchangeable general house.
        if (found.size() > 1) found.remove("home");
        return found;
    }
    public static int score(String requested, String saved, String keywords) {
        String query = normalize(requested), place = normalize(saved);
        if (query.isEmpty()) return 0;
        if (query.equals(place)) return 1000;
        Set<String> queryTypes = types(query), savedTypes = types(place);
        if (savedTypes.isEmpty()) savedTypes = types(normalize(keywords));
        if (!queryTypes.isEmpty() && !savedTypes.isEmpty() && Collections.disjoint(queryTypes, savedTypes)) return 0;
        // Explicit indoor/outdoor and modern/fantasy descriptors must not contradict.
        for (List<String> opposites : List.of(List.of("실내", "야외"), List.of("indoor", "outdoor"),
                List.of("현대", "중세"), List.of("modern", "medieval"))) {
            if (query.contains(opposites.get(0)) && place.contains(opposites.get(1))
                    || query.contains(opposites.get(1)) && place.contains(opposites.get(0))) return 0;
        }
        if (!queryTypes.isEmpty() && !Collections.disjoint(queryTypes, savedTypes)) return 100;
        // Explicit uploaded keywords may cover a longer place name; known scene types still cannot conflict.
        for (String alias : (keywords == null ? "" : keywords).split("[,;\\n]")) {
            String normalized = normalize(alias);
            if (normalized.length() >= 2 && (normalized.matches("[a-z0-9 ]+")
                    ? (" " + query + " ").contains(" " + normalized + " ") : query.contains(normalized))) return 200;
        }
        return 0;
    }
}
