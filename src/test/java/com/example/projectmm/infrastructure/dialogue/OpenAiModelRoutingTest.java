package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenAiModelRoutingTest {
    @Test void allThreeModelsRouteThroughOpenAiAndProduceParsedTurnsWithoutNetwork() {
        var mapper=new ObjectMapper();
        var gemini=mock(GeminiDialogueGateway.class);
        when(gemini.buildWorldRoomPromptParts(any())).thenReturn(new GeminiDialogueGateway.PromptParts("Existing narrative instructions", "Variable scene state"));
        var usage=new AiUsageTracker();
        var openai=new OpenAiDialogueGateway("test-never-sent","gpt-5.6-luna",mapper,gemini,usage);
        var requests=new ArrayList<String>();
        String payload=mapper.writeValueAsString(Map.of("reply","테스트 응답","elapsedSeconds",60));
        String response=mapper.writeValueAsString(Map.of("output",List.of(Map.of("type","message",
                "content",List.of(Map.of("type","output_text","text",payload)))),"usage",Map.of(
                "input_tokens",100,"output_tokens",20,"total_tokens",120,
                "input_tokens_details",Map.of("cached_tokens",40,"cache_write_tokens",30))));
        var client=RestClient.builder().baseUrl("https://api.openai.com")
                .requestInterceptor((request,body,execution)->{
                    var submitted=mapper.readTree(new String(body,StandardCharsets.UTF_8));
                    requests.add(submitted.path("model").asText());
                    assertThat(submitted.has("instructions")).isFalse();
                    assertThat(submitted.path("prompt_cache_options").path("mode").asText()).isEqualTo("explicit");
                    var fixed=submitted.path("input").path(0).path("content").path(0);
                    assertThat(fixed.path("prompt_cache_breakpoint").path("mode").asText()).isEqualTo("explicit");
                    assertThat(fixed.path("text").asText()).contains("Existing narrative instructions").doesNotContain("Variable scene state");
                    assertThat(submitted.path("input").path(1).path("content").asText()).isEqualTo("Variable scene state");
                    assertThat(submitted.path("input").path(2).path("content").asText()).isEqualTo("모델 연결 확인");
                    var result=new MockClientHttpResponse(response.getBytes(StandardCharsets.UTF_8),HttpStatus.OK);
                    result.getHeaders().setContentType(new org.springframework.http.MediaType("application","json",StandardCharsets.UTF_8));
                    return result;
                }).build();
        ReflectionTestUtils.setField(openai,"restClient",client);
        var router=new PocketDialogueGatewayRouter(gemini,openai);
        for(String model:List.of("gpt-5.6-luna","gpt-5.6-terra","gpt-5.6-sol")) {
            var context=mock(WorldRoomDialogueContext.class);
            when(context.aiProvider()).thenReturn("OPENAI");
            when(context.aiModel()).thenReturn(model);
            when(context.sessionId()).thenReturn(1L);
            when(context.recentMessages()).thenReturn(List.of());
            when(context.sessionSystems()).thenReturn(List.of());
            var turn=router.generateWorldRoomTurn(context,"모델 연결 확인");
            assertThat(turn.reply()).isEqualTo("테스트 응답");
            assertThat(turn.elapsedSeconds()).isEqualTo(60);
            assertThat(usage.latest(1L).model()).isEqualTo(model);
            assertThat(usage.latest(1L).cacheWriteTokens()).isEqualTo(30);
            assertThat(usage.latest(1L).promptTokenEstimate()).isNotNull();
            assertThat(usage.latest(1L).promptTokenEstimate().currentInputTokens()).isPositive();
        }
        assertThat(requests).containsExactly("gpt-5.6-luna","gpt-5.6-terra","gpt-5.6-sol");
        verify(gemini,never()).generateWorldRoomTurn(any(),anyString());
    }
}
