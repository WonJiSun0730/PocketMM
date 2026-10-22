package com.example.projectmm.domain.dialogue;

import java.util.*;

/** Small active-scene ledger. Observations, character claims and inferences have separate keys. */
public final class SceneFacts {
    public enum Kind { PERSON, POSSESSION, IDENTIFIER, DELIVERY, COMPLETION, UNCERTAIN }
    public enum Certainty { OBSERVED, CLAIM, INFERENCE }
    public enum Source { USER, REPLY, HISTORY }
    public enum Reason { NEW, EVENT, USER_DIRECTION, RETIRE }
    public record Fact(String key, Kind kind, String value, Certainty certainty, Source source, String evidence) {}
    public record Change(String key, Kind kind, String value, Certainty certainty, Source source,
                         String evidence, String previousValue, Reason reason) {}

    private SceneFacts() {}

    public static List<Fact> apply(List<Fact> before, List<Change> changes,
                                   String user, String reply, String history) {
        var facts = new LinkedHashMap<String, Fact>();
        for (var f : before) {
            validate(f.key(), f.kind(), f.value(), f.certainty(), f.source(), f.evidence());
            if (facts.putIfAbsent(f.key(), f) != null) throw invalid("Duplicate fact key");
        }
        var touched = new HashSet<String>();
        for (var c : changes) {
            if (c == null || !touched.add(c.key())) throw invalid("Repeated fact change");
            validate(c.key(), c.kind(), c.value(), c.certainty(), c.source(), c.evidence());
            if (c.reason() == null) throw invalid("Missing change reason");
            String evidenceSource = switch (c.source()) {
                case USER -> user;
                case REPLY -> reply;
                case HISTORY -> history;
            };
            if (evidenceSource == null || !evidenceSource.contains(c.evidence()))
                throw invalid("Fact evidence is not present in its source");
            Fact old = facts.get(c.key());
            if (old == null) {
                if (c.previousValue() != null || c.reason() != Reason.NEW)
                    throw invalid("New fact has an invalid predecessor");
            } else {
                if (!old.value().equals(c.previousValue()) || old.kind() != c.kind())
                    throw invalid("Fact predecessor does not match");
                if (c.reason() == Reason.NEW) throw invalid("Cannot overwrite an existing fact as new");
                if (c.reason() == Reason.USER_DIRECTION && c.source() != Source.USER)
                    throw invalid("Only user input can authorize a retcon");
                if (old.certainty() == Certainty.OBSERVED && c.certainty() != Certainty.OBSERVED)
                    throw invalid("Keep a claim or inference under its own key");
            }
            if (c.reason() == Reason.USER_DIRECTION && c.source() != Source.USER)
                throw invalid("User direction requires user evidence");
            if (c.reason() == Reason.RETIRE) {
                if (old == null) throw invalid("Cannot retire an unknown fact");
                facts.remove(c.key());
            } else {
                // A repeated mention is not a new event; keep the original provenance.
                if (old != null && old.value().equals(c.value()) && old.certainty() == c.certainty()) continue;
                facts.put(c.key(), new Fact(c.key(), c.kind(), c.value(), c.certainty(), c.source(), c.evidence()));
            }
        }
        if (facts.size() > 96) throw invalid("Active scene ledger exceeds 96 facts; resolve irrelevant facts first");
        return List.copyOf(facts.values());
    }

    private static void validate(String key, Kind kind, String value, Certainty certainty, Source source, String evidence) {
        if (key == null || key.isBlank() || key.length() > 120 || kind == null
                || value == null || value.isBlank() || value.length() > 400
                || certainty == null || source == null || evidence == null
                || evidence.isBlank() || evidence.length() > 800)
            throw invalid("Malformed scene fact");
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Scene continuity: " + message);
    }
}
