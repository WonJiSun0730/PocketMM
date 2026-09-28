package com.example.projectmm.application.chat;

import com.example.projectmm.domain.dialogue.DialoguePlayerStateChange;

public record PlayerStateUpdate(
        String status,
        int previousEnergy,
        int energy,
        int previousMood,
        int mood,
        String energyEvidence,
        String moodEvidence
) {
    public static PlayerStateUpdate resolve(
            int energy,
            int mood,
            String userText,
            DialoguePlayerStateChange proposal
    ) {
        if (proposal == null || !proposal.validFor(userText)) {
            return new PlayerStateUpdate(
                    "REJECTED",
                    energy,
                    energy,
                    mood,
                    mood,
                    "",
                    ""
            );
        }

        int nextEnergy = Math.clamp(
                energy + proposal.energyDelta(),
                0,
                100
        );

        int nextMood = Math.clamp(
                mood + proposal.moodDelta(),
                0,
                100
        );

        return new PlayerStateUpdate(
                nextEnergy == energy && nextMood == mood
                        ? "UNCHANGED"
                        : "APPLIED",
                energy,
                nextEnergy,
                mood,
                nextMood,
                proposal.energyDelta() == 0
                        ? ""
                        : proposal.energyEvidence(),
                proposal.moodDelta() == 0
                        ? ""
                        : proposal.moodEvidence()
        );
    }
}