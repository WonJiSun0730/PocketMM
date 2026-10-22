package com.example.projectmm.application.qa;
import java.util.*;
public final class PocketExperienceCode {
    public static Map<String,String> hashes() {
        var result=new TreeMap<String,String>();
        for(String name:List.of("application.chat.WorldRoomDialogueService","application.chat.WorldRoomSessionService",
                "application.dialogue.WorldRoomContextBuilder","application.dialogue.SceneContinuityService","application.dialogue.SceneContinuityPrompt","domain.dialogue.SceneFacts","infrastructure.dialogue.WorldRoomNarrativeStyle",
                "application.time.HybridTimeInterpreter","application.time.AiTimeInterpreter","application.time.RuleBasedTimeInterpreter","infrastructure.time.GeminiTimeParser","application.memory.MemoryRecallService","application.memory.MemoryRetrievalRanker","application.memory.MemoryCompressionService",
                "application.memory.PocketMemoryService","application.memory.PocketMemoryWriter",
                "application.memory.MemoryDecayPolicy","application.memory.MemoryTierPolicy","application.memory.MemoryLifecycleService",
                "application.memory.MemoryCompressionPersistenceService","application.memory.MemoryProvenanceAccessPolicy","application.memory.ProtectedMemoryDetailPolicy",
                "application.memory.HybridMemoryRetriever","application.memory.CachedMemoryEmbeddingService","application.memory.PocketMemoryQuery","application.memory.PocketMemoryDeduplicator","application.memory.RuleBasedMemoryCompressor","infrastructure.ai.GeminiMemoryEmbeddingProvider","domain.chat.WorldRoomSession","domain.chat.WorldRoomSessionCast","domain.chat.WorldRoomPlayerState","domain.memory.Memory","domain.memory.MemoryFact","domain.memory.SemanticMemory",
                "infrastructure.dialogue.AiUsageTracker","infrastructure.dialogue.PocketDialogueGatewayRouter","infrastructure.dialogue.OpenAiDialogueGateway","infrastructure.dialogue.GeminiDialogueGateway")) {
            String path="/com/example/projectmm/"+name.replace('.','/')+".class";
            try(var stream=PocketExperienceCode.class.getResourceAsStream(path)) {
                if(stream==null)throw new IllegalStateException("Missing service implementation: "+name);
                result.put(name,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            } catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        }
        return result;
    }
}
