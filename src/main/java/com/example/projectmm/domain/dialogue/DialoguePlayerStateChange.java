package com.example.projectmm.domain.dialogue;

public record DialoguePlayerStateChange(
        Integer energyDelta,
        Integer moodDelta,
        String energyEvidence,
        String moodEvidence
) {
    public static DialoguePlayerStateChange none() {
        return new DialoguePlayerStateChange(0, 0, "", "");
    }

    public static DialoguePlayerStateChange invalid() {
        return new DialoguePlayerStateChange(null, null, null, null);
    }

    public boolean validFor(String userText) {
        return validDelta(energyDelta)
                && validDelta(moodDelta)
                && validEvidence(userText, energyDelta, energyEvidence)
                && validEvidence(userText, moodDelta, moodEvidence);
    }

    private boolean validDelta(Integer delta) {
        return delta != null && delta >= -10 && delta <= 10;
    }

    private boolean validEvidence(String userText, int delta, String evidence) {
        if (delta == 0) return true;

        return evidence != null
                && !evidence.isBlank()
                && evidence.length() <= 240
                && userText != null
                && userText.contains(evidence);
    }
}