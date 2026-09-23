package com.example.projectmm.application.memory;

/** Conservative first-stage input: prose actions/private narration are not memory sources. */
public final class SpokenText {
    private SpokenText() {}

    public static String from(String input) {
        if (input == null || input.isBlank()) return "";
        // An unmatched marker is ambiguous: do not accidentally retain private narration.
        if (input.chars().filter(c -> c == '*').count() % 2 != 0) return "";
        String text = input.replaceAll("(?s)\\*[^*]*\\*", " ").strip();
        // OOC and parenthetical instructions are not in-character testimony.
        if (text.matches("(?is).*(?:\\booc\\b|\\[|\\]|\\(|\\)).*")) return "";
        return text;
    }
}
