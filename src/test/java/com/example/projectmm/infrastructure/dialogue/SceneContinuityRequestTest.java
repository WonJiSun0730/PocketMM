package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.SceneContinuityPrompt;
import com.example.projectmm.domain.chat.ChatRole;
import com.example.projectmm.domain.dialogue.*;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SceneContinuityRequestTest {
    final ObjectMapper json = new ObjectMapper();
    final String approved = "{\"approved\":true,\"turn\":null,\"changes\":[],\"conflicts\":[]}";
    WorldRoomDialogueContext context() {
        var c = PromptMigrationEquivalenceTest.world(false);
        return new WorldRoomDialogueContext(c.sessionId(),c.roomId(),c.roomTitle(),c.visibility(),c.simulatedAt(),
                c.world(),c.playerProfile(),c.introTitle(),c.introContent(),
                List.of(new DialogueMessageContext(ChatRole.CHARACTER,"Recent established scene",c.simulatedAt()),
                        new DialogueMessageContext(ChatRole.USER,"CURRENT_USER_UNIQUE",c.simulatedAt())),
                c.roomCast(),c.activeCast(),c.sessionSystems(),c.playerState(),c.recalledMemories(),
                c.userNote(),c.pocketPlayGuide(),c.noControlPlayer(),c.aiProvider(),c.aiModel(),
                "[{\"key\":\"receipt.custodian\",\"value\":\"FACT_UNIQUE\"}]");
    }
    String evidence(String mode) {
        var request = new LinkedHashMap<String,Object>();
        request.put("mode",mode);
        request.put("facts",json.readTree(context().sceneFactsJson()));
        request.put("userInput","CURRENT_USER_UNIQUE");
        request.put("candidate",mode.equals("BOOTSTRAP")?null:new WorldRoomTurn("The receipt stays in the notebook.",WorldRoomCastChange.none()));
        request.put("history",mode.equals("BOOTSTRAP")?List.of(Map.of("role","USER","content","CURRENT_USER_UNIQUE")):List.of());
        return json.writeValueAsString(request);
    }
    RestClient client(List<JsonNode> requests, boolean openAi) {
        return RestClient.builder().baseUrl("https://never-contact.invalid").requestInterceptor((request,body,execution)->{
            var sent=json.readTree(new String(body,StandardCharsets.UTF_8));requests.add(sent);
            boolean review=openAi?sent.path("text").path("format").path("name").asText().equals("scene_continuity_review")
                    :!sent.path("systemInstruction").toString().contains("[NARRATOR:");
            String payload=review?approved:json.writeValueAsString(Map.of("reply",approved,"enterCharacterIds",List.of(),
                    "leaveCharacterIds",List.of(),"systemChanges",List.of(),"elapsedSeconds",0));
            var response=openAi?Map.of("status","completed","usage",Map.of("input_tokens",100,"output_tokens",20,"total_tokens",120),
                    "output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",payload)))))
                    :Map.of("usageMetadata",Map.of("promptTokenCount",100,"candidatesTokenCount",20,"totalTokenCount",120),
                    "candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",payload))))));
            var result=new MockClientHttpResponse(json.writeValueAsString(response).getBytes(StandardCharsets.UTF_8),HttpStatus.OK);
            result.getHeaders().setContentType(MediaType.APPLICATION_JSON);return result;
        }).build();
    }
    @Test void bothProvidersUseDedicatedReviewWithoutNarratorOrDuplicatedFacts() {
        var gr=new ArrayList<JsonNode>();var or=new ArrayList<JsonNode>();
        var tracker=new AiUsageTracker();
        var gemini=new GeminiDialogueGateway("never-sent","fixture",json,client(gr,false));
        ReflectionTestUtils.setField(gemini,"usageTracker",tracker);
        var openai=new OpenAiDialogueGateway("never-sent","gpt-5.6-luna",json,gemini,tracker);
        ReflectionTestUtils.setField(openai,"restClient",client(or,true));
        assertThat(AiUsageTracker.withPurpose(7L,"SCENE_CONTINUITY",
                ()->gemini.reviewWorldRoomContinuity(context(),evidence("REVIEW")))).isEqualTo(approved);
        assertThat(AiUsageTracker.withPurpose(7L,"SCENE_CONTINUITY",
                ()->openai.reviewWorldRoomContinuity(context(),evidence("REVIEW")))).isEqualTo(approved);
        assertThat(gr).hasSize(1);assertThat(or).hasSize(1);
        assertThat(or.getFirst().path("model").asText()).isEqualTo("gpt-5.6-luna");
        String g=gr.getFirst().path("contents").path(0).path("parts").path(0).path("text").asText();
        String o=or.getFirst().path("input").path(1).path("content").path(0).path("text").asText()
                + or.getFirst().path("input").path(2).path("content").asText();
        assertThat(o).containsOnlyOnce("FACT_UNIQUE").containsOnlyOnce("CURRENT_USER_UNIQUE");
        assertThat(g).containsOnlyOnce("FACT_UNIQUE").containsOnlyOnce("CURRENT_USER_UNIQUE");
        assertThat(g).contains("RAW_IDENTITY","SOUL_PERSONALITY","VOICE_EXAMPLE","WORLD_FACT","MEMORY_EVIDENCE");
        assertThat(gr.getFirst().toString()).doesNotContain("[NARRATOR:","Fiction-writing and pacing:","Return JSON only:");
        assertThat(or.getFirst().toString()).doesNotContain("[NARRATOR:","Fiction-writing and pacing:");
        var properties=or.getFirst().path("text").path("format").path("schema").path("properties");
        assertThat(properties.has("approved")).isTrue();assertThat(properties.has("reply")).isFalse();
        assertThat(tracker.calls(7L)).hasSize(2).allMatch(c->c.purpose().equals("SCENE_CONTINUITY"));
        assertThat(tracker.calls(7L).getLast().usage().inputTokens()).isEqualTo(100);
    }
    @Test void bootstrapContainsOnlyItsOwnHistoryBatchAndNoDuplicateUserInput() {
        String evidence=SceneContinuityRequest.evidence(context(),evidence("BOOTSTRAP"));
        var data=json.readTree(evidence);
        assertThat(data.path("reference").path("recentMessages").size()).isZero();
        assertThat(evidence).containsOnlyOnce("CURRENT_USER_UNIQUE").doesNotContain("Recent established scene");
        assertThat(data.path("facts").size()).isEqualTo(1);
    }
    @Test void syntheticRequestTokenComparison() throws Exception {
        var requests=new ArrayList<JsonNode>();
        var gemini=new GeminiDialogueGateway("never-sent","fixture",json);
        var openai=new OpenAiDialogueGateway("never-sent","gpt-5.6-luna",json,gemini,new AiUsageTracker());
        ReflectionTestUtils.setField(openai,"restClient",client(requests,true));
        // Reproduce the prior review request shape with the same review instruction/evidence.
        openai.generateWorldRoomTurn(context(),SceneContinuityPrompt.REVIEW+"\n"+evidence("REVIEW"));
        openai.reviewWorldRoomContinuity(context(),evidence("REVIEW"));
        var encoding=Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);
        long before=encoding.countTokensOrdinary(requests.get(0).toString());
        long after=encoding.countTokensOrdinary(requests.get(1).toString());
        assertThat(after).isLessThan(before);
        Path report=Path.of("target/request-history-optimization.json");
        Files.writeString(report,json.writeValueAsString(Map.of("basis","SYNTHETIC_O200K_SERIALIZED_REQUEST_NOT_BILLING",
                "beforeReviewTokens",before,"afterReviewTokens",after,"savedTokens",before-after)));
    }
    @Test void routerKeepsProviderSelectionForDedicatedReviews() {
        var gemini=mock(GeminiDialogueGateway.class);var openai=mock(OpenAiDialogueGateway.class);
        var router=new PocketDialogueGatewayRouter(gemini,openai);
        when(openai.reviewWorldRoomContinuity(any(),anyString())).thenReturn(approved);
        assertThat(router.reviewWorldRoomContinuity(context(),"{}")).isEqualTo(approved);
        verify(openai).reviewWorldRoomContinuity(any(),eq("{}"));verifyNoInteractions(gemini);
    }

    @Test void dedicatedModelAndCacheBoundaryDoNotChangeFictionSelection() {
        var requests = new ArrayList<JsonNode>();
        var tracker = new AiUsageTracker();
        var gemini = new GeminiDialogueGateway("never-sent","fixture",json);
        var openai = new OpenAiDialogueGateway("never-sent","gpt-5.6-luna",json,gemini,tracker);
        ReflectionTestUtils.setField(openai,"restClient",client(requests,true));
        ReflectionTestUtils.setField(openai,"continuityModel","gpt-5.6-terra");
        openai.reviewWorldRoomContinuity(context(), evidence("REVIEW"));
        openai.generateWorldRoomTurn(context(),"continue");
        assertThat(requests.get(0).path("model").asText()).isEqualTo("gpt-5.6-terra");
        assertThat(requests.get(1).path("model").asText()).isEqualTo(context().aiModel());
        assertThat(tracker.calls(context().sessionId()).getFirst().usage().model()).isEqualTo("gpt-5.6-terra");
        var request = requests.getFirst();
        assertThat(request.path("prompt_cache_options").path("mode").asText()).isEqualTo("explicit");
        var stable = request.path("input").path(1).path("content").path(0);
        assertThat(stable.path("prompt_cache_breakpoint").path("mode").asText()).isEqualTo("explicit");
        assertThat(stable.path("text").asText()).contains("RAW_IDENTITY","WORLD_FACT")
                .doesNotContain("FACT_UNIQUE","CURRENT_USER_UNIQUE","MEMORY_EVIDENCE","simulatedAt");
        String dynamic = request.path("input").path(2).path("content").asText();
        assertThat(dynamic).contains("FACT_UNIQUE","MEMORY_EVIDENCE","simulatedAt");
        assertThat(SceneContinuityRequest.parts(context(), evidence("REVIEW")).stable())
                .isEqualTo(SceneContinuityRequest.parts(context(), evidence("VERIFY")).stable())
                .isEqualTo(SceneContinuityRequest.parts(context(), evidence("BOOTSTRAP")).stable());
    }

    @Test void solTargetUsesLunaForReviewButStillSolForFiction() {
        var requests = new ArrayList<JsonNode>();
        var tracker = new AiUsageTracker();
        var gemini = new GeminiDialogueGateway("never-sent","fixture",json);
        var openai = new OpenAiDialogueGateway("never-sent","gpt-5.6-sol",json,gemini,tracker);
        ReflectionTestUtils.setField(openai,"restClient",client(requests,true));
        var sol = context().withAiSelection("OPENAI","gpt-5.6-sol");
        openai.generateWorldRoomTurn(sol,"continue");
        openai.reviewWorldRoomContinuity(sol,evidence("REVIEW"));
        assertThat(requests.get(0).path("model").asText()).isEqualTo("gpt-5.6-sol");
        assertThat(requests.get(1).path("model").asText()).isEqualTo("gpt-5.6-luna");
        assertThat(tracker.calls(sol.sessionId())).extracting(c -> c.usage().model())
                .containsExactly("gpt-5.6-sol","gpt-5.6-luna");
    }

    @Test void internalStateJudgmentsUseLunaAndSelectedFictionModelRemainsFlexible() {
        var gemini=mock(GeminiDialogueGateway.class);var openai=mock(OpenAiDialogueGateway.class);
        var router=new PocketDialogueGatewayRouter(gemini,openai);
        var sol=context().withAiSelection("OPENAI","gpt-5.6-sol");
        router.initializeWorldRoomScene(sol,true,"opening");
        router.setupWorldRoomScene(sol,"setup");
        router.analyzeEditedWorldRoomState(sol,"edited");
        verify(openai).initializeWorldRoomScene(argThat(c->c.aiModel().equals("gpt-5.6-luna")),eq(true),eq("opening"));
        verify(openai).setupWorldRoomScene(argThat(c->c.aiModel().equals("gpt-5.6-luna")),eq("setup"));
        verify(openai).analyzeEditedWorldRoomState(argThat(c->c.aiModel().equals("gpt-5.6-luna")),eq("edited"));
        for(String model:List.of("gpt-5.6-luna","gpt-5.6-terra","gpt-5.6-sol")) {
            router.generateWorldRoomTurn(sol.withAiSelection("OPENAI",model),"continue");
            verify(openai).generateWorldRoomTurn(argThat(c->c.aiModel().equals(model)),eq("continue"));
        }
        verifyNoInteractions(gemini);
    }

    @Test void runtimeChangesStayAfterBoundaryAndAuthoredEditsInvalidatePrefix() {
        var c = context();
        var cast = c.roomCast().stream().map(x -> new WorldRoomCharacterContext(x.characterId(),
                x.characterName(),x.profile(),x.energy()+1,x.mood()+1,!x.active(),!x.departed())).toList();
        var systems = c.sessionSystems().stream().map(x -> new WorldRoomSystemContext(x.systemId(),
                x.name(),x.currentValue()+1,x.minValue(),x.maxValue(),x.changeCondition(),x.states())).toList();
        var next = new WorldRoomDialogueContext(c.sessionId(),c.roomId(),c.roomTitle(),c.visibility(),
                c.simulatedAt().plusSeconds(60),c.world(),c.playerProfile(),c.introTitle(),c.introContent(),
                List.of(),cast,c.activeCast(),systems,c.playerState(),List.of(),c.userNote(),
                c.pocketPlayGuide(),c.noControlPlayer(),c.aiProvider(),c.aiModel(),c.sceneFactsJson());
        var oldParts = SceneContinuityRequest.parts(c,evidence("REVIEW"));
        var newParts = SceneContinuityRequest.parts(next,evidence("VERIFY"));
        assertThat(newParts.stable()).isEqualTo(oldParts.stable());
        assertThat(newParts.dynamic()).isNotEqualTo(oldParts.dynamic());
        var edited = new WorldRoomDialogueContext(c.sessionId(),c.roomId(),c.roomTitle(),c.visibility(),
                c.simulatedAt(),c.world(),c.playerProfile(),c.introTitle(),"EDITED_SETTING",c.recentMessages(),
                c.roomCast(),c.activeCast(),c.sessionSystems(),c.playerState(),c.recalledMemories(),c.userNote(),
                c.pocketPlayGuide(),c.noControlPlayer(),c.aiProvider(),c.aiModel(),c.sceneFactsJson());
        assertThat(SceneContinuityRequest.parts(edited,evidence("REVIEW")).stable()).isNotEqualTo(oldParts.stable());
    }
}
