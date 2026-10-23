package com.example.projectmm.infrastructure.dialogue;

import com.example.projectmm.application.dialogue.ReplySuggestions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiReplySuggestionsTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void oneLunaCallReturnsThreeShortDraftsAndTracksAuxiliaryUsageWithoutRoleplay() {
        var usage=new AiUsageTracker();var builder=mock(GeminiDialogueGateway.class);
        var gateway=new OpenAiDialogueGateway("offline-test-key","gpt-5.6-sol",json,builder,usage);
        var count=new AtomicInteger();
        String payload=json.writeValueAsString(Map.of("suggestions",List.of(
                Map.of("action","문을 바라본다.","speech","함께 가죠."),
                Map.of("action","","speech","조금 더 설명해 주시겠어요?"),
                Map.of("action","조용히 고개를 끄덕인다.","speech",""))));
        var client=RestClient.builder().baseUrl("https://api.openai.com").requestInterceptor((request,body,execution)->{
            count.incrementAndGet();var sent=json.readTree(new String(body,StandardCharsets.UTF_8));
            assertThat(sent.path("model").asText()).isEqualTo("gpt-5.6-luna");
            assertThat(sent.path("max_output_tokens").asInt()).isEqualTo(800);
            assertThat(sent.path("reasoning").path("effort").asText()).isEqualTo("none");
            assertThat(sent.path("stream").asBoolean()).isFalse();
            assertThat(sent.path("input").path(1).path("content").asText()).isEqualTo("scene evidence");
            assertThat(sent.path("text").path("format").path("strict").asBoolean()).isTrue();
            var response=json.writeValueAsString(Map.of("status","completed","output",List.of(Map.of("type","message",
                    "content",List.of(Map.of("type","output_text","text",payload)))),
                    "usage",Map.of("input_tokens",100,"output_tokens",70,"total_tokens",170)));
            return new MockClientHttpResponse(response.getBytes(StandardCharsets.UTF_8),HttpStatus.OK);
        }).build();
        ReflectionTestUtils.setField(gateway,"restClient",client);
        assertThat(gateway.suggestPlayerReplies(1L,"scene evidence")).containsExactly("*문을 바라본다.* 함께 가죠.","조금 더 설명해 주시겠어요?","*조용히 고개를 끄덕인다.*");
        assertThat(count.get()).isEqualTo(1);
        assertThat(usage.calls(1L)).singleElement().satisfies(call->{
            assertThat(call.purpose()).isEqualTo("REPLY_SUGGESTION");
            assertThat(call.usage().model()).isEqualTo("gpt-5.6-luna");
        });
        assertThat(usage.latest(1L)).isNull();verifyNoInteractions(builder);
    }
    @Test void normalizesFormattingAndLengthWithoutDiscardingPaidDrafts() {
        var drafts=List.of(new ReplySuggestions.Draft("*고개를 끄덕인다.*\n잠시 기다린다.","첫 문장. 두 번째 문장."),
                new ReplySuggestions.Draft("가".repeat(81),"둘"),new ReplySuggestions.Draft("","셋"));
        var result=ReplySuggestions.parse(json.writeValueAsString(new ReplySuggestions.Payload(drafts)));
        assertThat(result).hasSize(3);
        assertThat(result.getFirst()).isEqualTo("*고개를 끄덕인다.* 첫 문장.");
        assertThat(result.get(1)).contains("…").doesNotContain("가".repeat(81));
    }
    @Test void duplicateOrEmptyDraftDoesNotDiscardOtherAvailableChoices() {
        var drafts=List.of(new ReplySuggestions.Draft("","같음"),new ReplySuggestions.Draft("","같음"),new ReplySuggestions.Draft("","셋"));
        assertThat(ReplySuggestions.parse(json.writeValueAsString(new ReplySuggestions.Payload(drafts)))).containsExactly("같음","셋");
        assertThatThrownBy(()->ReplySuggestions.parse("invalid JSON"))
                .isInstanceOfSatisfying(ReplySuggestions.Failure.class,failure->{
                    assertThat(failure.code()).isEqualTo("INVALID_JSON");assertThat(failure.payload()).isEqualTo("invalid JSON");
                });
    }
    @Test void providerRejectionKeepsSafeCodeAndParameterWithoutProviderMessageOrRetry() {
        var usage=new AiUsageTracker();var gateway=new OpenAiDialogueGateway("offline-key","gpt-5.6-sol",json,mock(GeminiDialogueGateway.class),usage);
        var calls=new AtomicInteger();
        var client=RestClient.builder().baseUrl("https://api.openai.com").requestInterceptor((request,body,execution)->{
            calls.incrementAndGet();
            String error="{\"error\":{\"code\":\"unsupported_parameter\",\"param\":\"text.format.schema\",\"message\":\"SECRET provider data\"}}";
            return new MockClientHttpResponse(error.getBytes(StandardCharsets.UTF_8),HttpStatus.BAD_REQUEST);
        }).build();
        ReflectionTestUtils.setField(gateway,"restClient",client);
        assertThatThrownBy(()->gateway.suggestPlayerReplies(1L,"private scene evidence"))
                .isInstanceOfSatisfying(ReplySuggestions.Failure.class,failure->{
                    assertThat(failure.code()).isEqualTo("HTTP_400");
                    assertThat(failure.getMessage()).contains("unsupported_parameter","text.format.schema").doesNotContain("SECRET","private scene evidence");
                });
        assertThat(calls.get()).isEqualTo(1);
        assertThat(usage.calls(1L)).singleElement().satisfies(call->assertThat(call.status()).isEqualTo("REQUEST_FAILED"));
    }
}
