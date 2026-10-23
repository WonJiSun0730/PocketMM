package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.domain.dialogue.WorldRoomDialogueContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.*;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiTargetStreamingTest {
    @Test void selectedTargetStreamsOnceAndTerminalUsageAndStateAreStillParsed() {
        for(String status:List.of("completed","incomplete")) {
            var mapper=new ObjectMapper();var prompts=mock(GeminiDialogueGateway.class);
            when(prompts.buildWorldRoomPromptParts(any())).thenReturn(new GeminiDialogueGateway.PromptParts("policy","scene"));
            var usage=new AiUsageTracker();var gateway=new OpenAiDialogueGateway("never-sent","gpt-5.6-luna",mapper,prompts,usage);
            var context=mock(WorldRoomDialogueContext.class);
            when(context.aiModel()).thenReturn("gpt-5.6-sol");when(context.sessionId()).thenReturn(1L);
            when(context.recentMessages()).thenReturn(List.of());when(context.sessionSystems()).thenReturn(List.of());
            var seen=new ArrayList<String>();var sends=new java.util.concurrent.atomic.AtomicInteger();
            var payload="{\"reply\":\"hello\",\"elapsedSeconds\":60}";
            var finalResponse=Map.of("status",status,"output",List.of(Map.of("type","message","content",List.of(Map.of("type","output_text","text",payload)))),
                    "usage",Map.of("input_tokens",100,"output_tokens",20,"total_tokens",120,"input_tokens_details",Map.of("cached_tokens",40,"cache_write_tokens",0)));
            var sse=OpenAiResponseStreamTest.delta(payload)+OpenAiResponseStreamTest.frame(Map.of("type","response."+status,"response",finalResponse));
            var client=RestClient.builder().baseUrl("https://api.openai.com").requestInterceptor((r,b,execution)->{
                sends.incrementAndGet();var request=mapper.readTree(b);
                assertThat(request.path("stream").asBoolean()).isTrue();assertThat(request.path("model").asText()).isEqualTo("gpt-5.6-sol");
                assertThat(request.path("text").path("format").path("schema").path("properties").properties().iterator().next().getKey()).isEqualTo("reply");
                var response=new MockClientHttpResponse(sse.getBytes(StandardCharsets.UTF_8),HttpStatus.OK);
                response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);return response;
            }).build();ReflectionTestUtils.setField(gateway,"restClient",client);
            var timing=new DialogueTiming();
            java.util.function.Supplier<com.example.projectmm.domain.dialogue.WorldRoomTurn> run=()->timing.measure(()->
                    DialogueTokens.deliver(seen::add,()->DialogueTokens.generate(()->gateway.generateWorldRoomTurn(context,"continue"))));
            if(status.equals("completed")) {var turn=run.get();assertThat(turn.reply()).isEqualTo("hello");assertThat(turn.elapsedSeconds()).isEqualTo(60);}
            else assertThatThrownBy(run::get).hasMessageContaining("Failed to parse");
            assertThat(sends).hasValue(1);assertThat(seen).containsExactly("hello");assertThat(timing.snapshot().firstTokenMs()).isNotNull();
            assertThat(usage.latest(1L).outputTokens()).isEqualTo(20);
        }
    }
}
