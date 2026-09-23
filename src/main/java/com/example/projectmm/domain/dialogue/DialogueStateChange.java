package com.example.projectmm.domain.dialogue;

public record DialogueStateChange(Integer energyDelta, Integer moodDelta,
                                  String energyEvidence, String moodEvidence) {
    public static DialogueStateChange none() { return new DialogueStateChange(0, 0, "", ""); }
    public static DialogueStateChange invalid() { return new DialogueStateChange(null, null, null, null); }

    public boolean validFor(String reply) {
        // 숫자 범위와 실제 출력의 인용을 검사한다. 원인의 의미 적합성은 별도 RP QA 대상이다.
        return validDelta(energyDelta) && validDelta(moodDelta)
                && validEvidence(reply, energyDelta, energyEvidence)
                && validEvidence(reply, moodDelta, moodEvidence);
    }

    private boolean validDelta(Integer delta) { return delta != null && delta >= -10 && delta <= 10; }
    private boolean validEvidence(String reply, int delta, String evidence) {
        if (delta == 0) return true;
        return evidence != null && !evidence.isBlank() && evidence.length() <= 240
                && reply != null && reply.contains(evidence);
    }
}
