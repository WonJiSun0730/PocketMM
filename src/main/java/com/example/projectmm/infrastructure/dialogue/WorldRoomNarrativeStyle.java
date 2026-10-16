package com.example.projectmm.infrastructure.dialogue;

/** Shared by both providers through the world-room system prompt. */
final class WorldRoomNarrativeStyle {
    static String instruction(boolean noControlPlayer) {
        String participation = noControlPlayer ? PromptModules.section("SCENE", "live.strict") : PromptModules.section("SCENE", "live.elaborate");
        return PromptModules.section("SCENE", "live.pacing") + participation;
    }
}
