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
        String g=gr.getFirst().path("contents").path(0).path("parts").path(0).path("text").asText();
        String o=or.getFirst().path("input").path(0).path("content").asText();
        assertThat(g).isEqualTo(o).containsOnlyOnce("FACT_UNIQUE").containsOnlyOnce("CURRENT_USER_UNIQUE");
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
}
