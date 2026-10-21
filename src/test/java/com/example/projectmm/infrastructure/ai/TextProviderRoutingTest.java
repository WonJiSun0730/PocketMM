package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.domain.chat.*;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import com.example.projectmm.infrastructure.time.GeminiTimeParser;
import com.example.projectmm.infrastructure.memory.*;
import com.example.projectmm.application.memory.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TextProviderRoutingTest {
    final ObjectMapper mapper=new ObjectMapper();
    final AiUsageTracker usage=new AiUsageTracker();
    final WorldRoomSessionRepository sessions=mock(WorldRoomSessionRepository.class);
    final WorldRoomSession session=mock(WorldRoomSession.class);
    final MockEnvironment env=new MockEnvironment().withProperty("openai.api-key","openai-test")
            .withProperty("gemini.api-key","gemini-test");
    TextProviderRouting routing;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        when(sessions.findById(7L)).thenReturn(Optional.of(session));when(session.getAiProvider()).thenReturn("OPENAI");
        routing=new TextProviderRouting(sessions,env,mapper,usage);
        var builder=RestClient.builder().baseUrl("https://api.openai.com");
        server=MockRestServiceServer.bindTo(builder).build();ReflectionTestUtils.setField(routing,"client",builder.build());
    }
    String response(String text) {
        return mapper.writeValueAsString(Map.of("status","completed","output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",text)))),
                "usage",Map.of("input_tokens",20,"output_tokens",10,"total_tokens",30,"input_tokens_details",Map.of("cached_tokens",0))));
    }
    @Test void bothKeysStillUseOpenAiForTimeAndNeverTouchGeminiTransport() {
        var parser=new GeminiTimeParser("gemini-test","gemini-model",mapper);
        ReflectionTestUtils.setField(parser,"textRouting",routing);
        var forbidden=RestClient.builder().requestInterceptor((r,b,e)->{throw new AssertionError("Gemini request escaped");}).build();
        ReflectionTestUtils.setField(parser,"restClient",forbidden);
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(header("Authorization","Bearer openai-test"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("gpt-5.6-luna")))
                .andRespond(withSuccess(response("{\"matched\":true,\"type\":\"RELATIVE\",\"amount\":2,\"unit\":\"HOUR\",\"precision\":\"EXACT\",\"confidence\":1.0}"),MediaType.APPLICATION_JSON));
        var result=AiUsageTracker.withScope(7L,"ROLEPLAY",()->parser.parse("두 시간이 지났다",LocalDateTime.of(2026,10,16,12,0)));
        assertThat(result.matched()).isTrue();assertThat(result.amount()).isEqualTo(2);
        assertThat(usage.calls(7L)).singleElement().satisfies(c->{assertThat(c.purpose()).isEqualTo("TIME_INTERPRETATION");assertThat(c.usage().provider()).isEqualTo("OPENAI");});
        server.verify();assertThat(AiUsageTracker.currentSessionId()).isNull();
    }
    @Test void openAiEmbeddingUsesSeparateCacheAndOpenAiEndpoint() {
        var provider=new GeminiMemoryEmbeddingProvider("gemini-test","gemini-embedding-001",mapper);
        ReflectionTestUtils.setField(provider,"textRouting",routing);
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[0.25,0.75]}],\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}",MediaType.APPLICATION_JSON));
        AiUsageTracker.withScope(7L,"ROLEPLAY",()->{
            assertThat(provider.cacheNamespace()).isEqualTo("OPENAI:text-embedding-3-small");
            assertThat(provider.embed("기억")).containsExactly(.25,.75);return null;
        });server.verify();
        when(session.getAiProvider()).thenReturn("GEMINI");
        assertThat(AiUsageTracker.withScope(7L,"ROLEPLAY",provider::cacheNamespace)).isEqualTo("GEMINI:gemini-embedding-001");
    }
    @Test void missingOpenAiKeyCannotFallBackEvenWhenGeminiKeyExists() {
        env.setProperty("openai.api-key","");
        assertThatThrownBy(()->AiUsageTracker.withScope(7L,"MEMORY_EMBEDDING",()->routing.embed("secret")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fallback is blocked");
        server.verify();assertThat(AiUsageTracker.currentSessionId()).isNull();
    }
    @Test void memoryExtractionSelectsProviderPerCallRatherThanPerAvailableKey() {
        var extractor=new MemoryExtractionConfiguration().chatMemoryExtractor(env,mapper,routing,usage);
        var selected=AiUsageTracker.withScope(7L,"MEMORY_EXTRACTION",()->ReflectionTestUtils.invokeMethod(extractor,"selected"));
        assertThat(selected).isInstanceOf(OpenAiChatMemoryExtractor.class);
        when(session.getAiProvider()).thenReturn("GEMINI");
        var next=AiUsageTracker.withScope(7L,"MEMORY_EXTRACTION",()->ReflectionTestUtils.invokeMethod(extractor,"selected"));
        assertThat(next).isInstanceOf(GeminiChatMemoryExtractor.class);
    }
    @Test void invalidPocketFailsClosedAndScopeIsRestored() {
        assertThatThrownBy(()->AiUsageTracker.withScope(999L,"ROLEPLAY",routing::openAi)).isInstanceOf(IllegalStateException.class);
        assertThat(AiUsageTracker.currentSessionId()).isNull();
    }
    @Test void compressionUsesTheMemoryPocketEvenWithoutLiveTurnScope() {
        var prompts=mock(MemoryCompressionPromptBuilder.class);
        var memory=mock(com.example.projectmm.domain.memory.Memory.class);
        when(memory.getWorldRoomSession()).thenReturn(session);
        when(session.getId()).thenReturn(7L);
        when(prompts.prepare(any(),any(),any())).thenReturn(Optional.of(new MemoryCompressionPrompt("Return JSON summary",List.of(9L))));
        var compressor=new GeminiMemoryCompressor("gemini-test","gemini-model",mapper,prompts);
        ReflectionTestUtils.setField(compressor,"textRouting",routing);
        ReflectionTestUtils.setField(compressor,"client",RestClient.builder().requestInterceptor((r,b,e)->{throw new AssertionError("Gemini compression escaped");}).build());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(response("{\"summary\":\"compressed memory\"}"),MediaType.APPLICATION_JSON));
        var result=compressor.compress(memory,com.example.projectmm.domain.memory.MemoryDetailLevel.SUMMARY,List.of());
        assertThat(result.summary()).isEqualTo("compressed memory");
        assertThat(result.evidenceMemoryIds()).containsExactly(9L);
        server.verify();assertThat(AiUsageTracker.currentSessionId()).isNull();
    }
    @Test void historicalAbsenceContextUsesCurrentPocketProvider() {
        var gemini=mock(com.example.projectmm.infrastructure.dialogue.GeminiDialogueGateway.class);
        var openai=mock(com.example.projectmm.infrastructure.dialogue.OpenAiDialogueGateway.class);
        var router=new com.example.projectmm.infrastructure.dialogue.PocketDialogueGatewayRouter(gemini,openai);
        ReflectionTestUtils.setField(router,"sessions",sessions);
        var old=new com.example.projectmm.domain.dialogue.WorldRoomDialogueContext(7L,null,null,null,null,null,null,null,null,
                List.of(),List.of(),List.of(),List.of(),null,List.of(),null,null,false,"GEMINI","old-model");
        router.generateWorldRoomTurn(old,"absence");
        verify(openai).generateWorldRoomTurn(argThat(c->c.aiProvider().equals("OPENAI")),eq("absence"));
        verifyNoInteractions(gemini);
    }
}
