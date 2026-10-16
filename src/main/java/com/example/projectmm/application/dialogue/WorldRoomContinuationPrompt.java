package com.example.projectmm.application.dialogue;

import com.example.projectmm.infrastructure.dialogue.PromptModules;
public final class WorldRoomContinuationPrompt {
    private WorldRoomContinuationPrompt() {}

    public static String build(boolean noControlPlayer) {
        return build(noControlPlayer, 1);
    }

    public static String build(boolean noControlPlayer, int consecutiveNo) {
        if (consecutiveNo < 1) throw new IllegalArgumentException("Continuation number must be positive");
        String scope = noControlPlayer ? "STRICT" : consecutiveNo <= 2 ? "LOW"
                : consecutiveNo <= 5 ? "MODERATE" : "HIGH";
        String header = "[Current automatic progression directive: consecutiveNo=" + consecutiveNo
                + "; playerScope=" + scope + "]\n";
        header += PromptModules.section("SCENE", "continuation.progress");
        if (noControlPlayer) return header + PromptModules.section("SCENE", "continuation.strict");
        if (consecutiveNo <= 2) return header + PromptModules.section("SCENE", "continuation.low");
        String delegated = consecutiveNo <= 5 ? PromptModules.section("SCENE", "continuation.moderate") : PromptModules.section("SCENE", "continuation.high");
        return header + PromptModules.section("SCENE", "continuation.delegated-intro") + delegated + PromptModules.section("SCENE", "continuation.delegated-end");
    }
}
