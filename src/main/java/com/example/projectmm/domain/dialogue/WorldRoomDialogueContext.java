package com.example.projectmm.domain.dialogue;

import com.example.projectmm.domain.chat.WorldConversation;

import java.time.LocalDateTime;
import java.util.List;

public record WorldRoomDialogueContext(
        Long sessionId,
        Long roomId,
        String roomTitle,
        WorldConversation.Visibility visibility,
        LocalDateTime simulatedAt,
        DialogueWorldContext world,
        DialoguePlayerProfile playerProfile,
        String introTitle,
        String introContent,
        List<DialogueMessageContext> recentMessages,
        List<WorldRoomCharacterContext> roomCast,
        List<WorldRoomCharacterContext> activeCast,
        List<WorldRoomSystemContext> sessionSystems,
        WorldRoomPlayerStateContext playerState,
        List<CharacterMemories> recalledMemories,
        String userNote,
        String pocketPlayGuide,
        boolean noControlPlayer,
        String aiProvider,
        String aiModel,
        String sceneFactsJson
) {
    public WorldRoomDialogueContext(
            Long roomId,
            String roomTitle,
            WorldConversation.Visibility visibility,
            LocalDateTime simulatedAt,
            DialogueWorldContext world,
            DialoguePlayerProfile playerProfile,
            String introTitle,
            String introContent,
            List<DialogueMessageContext> recentMessages,
            List<WorldRoomCharacterContext> roomCast,
            List<WorldRoomCharacterContext> activeCast,
            List<WorldRoomSystemContext> sessionSystems,
            WorldRoomPlayerStateContext playerState,
            List<CharacterMemories> recalledMemories,
            String userNote,
            String pocketPlayGuide,
            boolean noControlPlayer
    ) {
        this(null, roomId, roomTitle, visibility, simulatedAt, world, playerProfile,
                introTitle, introContent, recentMessages, roomCast, activeCast,
                sessionSystems, playerState, recalledMemories, userNote, pocketPlayGuide,
                noControlPlayer, "GEMINI", null);
    }

    public WorldRoomDialogueContext(
            Long roomId,
            String roomTitle,
            WorldConversation.Visibility visibility,
            LocalDateTime simulatedAt,
            DialogueWorldContext world,
            DialoguePlayerProfile playerProfile,
            String introTitle,
            String introContent,
            List<DialogueMessageContext> recentMessages,
            List<WorldRoomCharacterContext> roomCast,
            List<WorldRoomCharacterContext> activeCast,
            List<WorldRoomSystemContext> sessionSystems
    ) {
        this(
                null,
                roomId,
                roomTitle,
                visibility,
                simulatedAt,
                world,
                playerProfile,
                introTitle,
                introContent,
                recentMessages,
                roomCast,
                activeCast,
                sessionSystems,
                WorldRoomPlayerStateContext.empty(),
                List.of(),
                null,
                null,
                false,
                "GEMINI",
                null
        );
    }

    public WorldRoomDialogueContext(Long sessionId, Long roomId, String roomTitle,
            WorldConversation.Visibility visibility, LocalDateTime simulatedAt, DialogueWorldContext world,
            DialoguePlayerProfile playerProfile, String introTitle, String introContent,
            List<DialogueMessageContext> recentMessages, List<WorldRoomCharacterContext> roomCast,
            List<WorldRoomCharacterContext> activeCast, List<WorldRoomSystemContext> sessionSystems,
            WorldRoomPlayerStateContext playerState, List<CharacterMemories> recalledMemories,
            String userNote, String pocketPlayGuide, boolean noControlPlayer, String aiProvider, String aiModel) {
        this(sessionId, roomId, roomTitle, visibility, simulatedAt, world, playerProfile, introTitle,
                introContent, recentMessages, roomCast, activeCast, sessionSystems, playerState,
                recalledMemories, userNote, pocketPlayGuide, noControlPlayer, aiProvider, aiModel, null);
    }

    public WorldRoomDialogueContext withSceneFacts(String json) {
        return new WorldRoomDialogueContext(sessionId,roomId,roomTitle,visibility,simulatedAt,world,playerProfile,
                introTitle,introContent,recentMessages,roomCast,activeCast,sessionSystems,playerState,recalledMemories,
                userNote,pocketPlayGuide,noControlPlayer,aiProvider,aiModel,json);
    }

    public WorldRoomDialogueContext withAiSelection(String provider,String model) {
        return new WorldRoomDialogueContext(sessionId,roomId,roomTitle,visibility,simulatedAt,world,playerProfile,
                introTitle,introContent,recentMessages,roomCast,activeCast,sessionSystems,playerState,recalledMemories,
                userNote,pocketPlayGuide,noControlPlayer,provider,model,sceneFactsJson);
    }
    public record CharacterMemories(
            Long characterId,
            String characterName,
            List<RecalledMemory> memories
    ) {}

    public record RecalledMemory(
            Long memoryId,
            String summary,
            String evidenceQuote,
            int importance,
            int effectiveStrength,
            LocalDateTime rememberedAt
    ) {}
}
