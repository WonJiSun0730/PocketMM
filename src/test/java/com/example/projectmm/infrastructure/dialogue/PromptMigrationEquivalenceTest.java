package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.chat.WorldConversation;
import com.example.projectmm.application.dialogue.WorldRoomContinuationPrompt;
import com.example.projectmm.application.chat.PocketActivityPrompt;
import com.example.projectmm.application.policy.ContentMode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Fingerprints captured from GitHub 27ba06f BEFORE externalizing any instructions. */
class PromptMigrationEquivalenceTest {
    static final ObjectMapper M=new ObjectMapper();
    static final LocalDateTime AT=LocalDateTime.of(2030,1,1,12,0);
    static WorldRoomDialogueContext world(boolean strict) {
        var profile=new DialogueCharacterProfile("short","RAW_IDENTITY",List.of("VOICE_EXAMPLE"),"SOUL_PERSONALITY","Korean","BACKGROUND","APPEARANCE");
        var character=new WorldRoomCharacterContext(1L,"FixtureNPC",profile,70,60,true);
        var absent=new WorldRoomCharacterContext(2L,"AbsentNPC",profile,40,50,false,true);
        return new WorldRoomDialogueContext(7L,3L,"Fixture room",WorldConversation.Visibility.PRIVATE,AT,
                new DialogueWorldContext(4L,"Fixture world","WORLD_GUIDE",List.of(new DialogueWorldLore("FACT","law","WORLD_FACT")),List.of()),
                new DialoguePlayerProfile("Player","PLAYER_PROFILE"),"Intro","INTRO_CONTEXT",List.of(),
                List.of(character,absent),List.of(character),List.of(),new WorldRoomPlayerStateContext("STORED_LOCATION",List.of("tired"),List.of("notebook")),
                List.of(new WorldRoomDialogueContext.CharacterMemories(1L,"FixtureNPC",List.of(new WorldRoomDialogueContext.RecalledMemory(9L,"MEMORY_SUMMARY","MEMORY_EVIDENCE",7,70,AT)))),
                "USER_NOTE","POCKET_GUIDE",strict,"OPENAI","gpt-5.6-luna");
    }
    static Map<String,String> prompts() {
        var gateway=new GeminiDialogueGateway("never-sent","fixture",M);
        var result=new TreeMap<String,String>();
        for(boolean strict:new boolean[]{false,true}) {
            var parts=gateway.buildWorldRoomPromptParts(world(strict));
            result.put("world-"+strict+"-stable",parts.stable());
            result.put("world-"+strict+"-dynamic",parts.dynamic());
            for(int count:new int[]{1,3,6})result.put("continue-"+strict+"-"+count,WorldRoomContinuationPrompt.build(strict,count));
        }
        var legacy=new DialogueContext(1L,"FixtureNPC",null,70,60,AT,ContentMode.NORMAL,List.of(),
                List.of(new DialogueMemoryContext("LEGACY_MEMORY",7,70,AT)),List.of(),new DialoguePlayerProfile("Player","PLAYER_PROFILE"),60,50,List.of());
        var request=M.valueToTree(ReflectionTestUtils.invokeMethod(gateway,"createRequest",legacy,"hello"));
        result.put("legacy",request.path("systemInstruction").path("parts").path(0).path("text").asText());
        result.put("absence-plan",PocketActivityPrompt.build(AT,AT.plusHours(2)));
        return result;
    }
    static String normalize(String value) {return value.replace("\r\n","\n").replaceAll("(?m)[ \\t]+$","").strip();}
    static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalize(value).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void allExistingInstructionsStayEquivalent() throws Exception {
        var actual=new TreeMap<String,String>();
        for(var e:prompts().entrySet())actual.put(e.getKey(),digest(e.getValue()));
        Path baseline=Path.of("src/test/resources/prompts/pre-modularization-sha256.json");
        var expected=M.readTree(Files.readString(baseline));
        assertThat((tools.jackson.databind.JsonNode)M.valueToTree(actual)).isEqualTo(expected);
    }
}
