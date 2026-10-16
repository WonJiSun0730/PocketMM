package com.example.projectmm.qa;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QaPersonaClientTest {
    private QaSpec spec() {
        var m=QaSpec.mock("client",20);var rate=new QaSpec.Rate(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,0,BigDecimal.ONE,BigDecimal.ONE,"test rate","2026-10-15");
        return new QaSpec(m.runId(),20,true,m.runId(),m.persona(),"gpt-5.6-luna","target-model","extractor-model",BigDecimal.ONE,BigDecimal.ONE,
                Map.of("gpt-5.6-luna",rate,"target-model",rate,"extractor-model",rate),3,true);
    }
    @Test void smallRequestContainsOnlyPersonaAndRecentConversation() {
        var spec=spec();var client=new QaPersonaClient(spec,"never-sent",RestClient.create());
        var request=client.request(new QaRunner.PersonaInput(spec.persona(),1,List.of(new QaRunner.Exchange("이전 입력","실제 상대 반응"))));
        String json=new ObjectMapper().writeValueAsString(request);
        assertThat(json).contains("실제 상대 반응","gpt-5.6-luna").doesNotContain("target-model","extractor-model","recalledMemories","B731","expected","oracle");
        assertThat(request.get("store")).isEqualTo(false);assertThat(json.length()).isLessThan(3000);
        assertThat(request).doesNotContainKeys("previous_response_id","conversation");
    }
    @Test void reportedUsageIsKeptEvenWhenOutputIsIncomplete() {
        var builder=RestClient.builder().baseUrl("https://api.openai.com");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("""
                {"id":"resp_fixture","status":"incomplete","usage":{"input_tokens":100,"input_tokens_details":{"cached_tokens":40,"cache_write_tokens":10},"output_tokens":20}}
                """,MediaType.APPLICATION_JSON));
        var client=new QaPersonaClient(spec(),"never-sent",builder.build());
        var result=client.next(new QaRunner.PersonaInput(spec().persona(),1,List.of()));
        assertThat(result.error()).contains("incomplete");assertThat(result.calls()).hasSize(1);
        assertThat(result.calls().getFirst().usd()).isEqualByComparingTo("0.000120");server.verify();
    }
    @Test void missingWriteUsageIsUnknownRatherThanFree() {
        var builder=RestClient.builder().baseUrl("https://api.openai.com");var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("""
                {"status":"completed","usage":{"input_tokens":100,"input_tokens_details":{"cached_tokens":0},"output_tokens":20},"output":[{"type":"message","content":[{"type":"output_text","text":"같이 걸을래?"}]}]}
                """,MediaType.APPLICATION_JSON));
        var result=new QaPersonaClient(spec(),"never-sent",builder.build()).next(new QaRunner.PersonaInput(spec().persona(),1,List.of()));
        assertThat(result.text()).isEqualTo("같이 걸을래?");assertThat(result.calls().getFirst().usd()).isNull();server.verify();
    }
    @Test void paidPersonaCannotBeChangedToAnotherModel() {
        var s=spec();
        assertThatThrownBy(()->new QaSpec(s.runId(),s.turns(),true,s.approval(),s.persona(),"gpt-5.6-terra",s.targetModel(),s.extractionModel(),
                s.budgetUsd(),s.turnReservationUsd(),s.prices(),s.recentTurns(),true)).hasMessageContaining("must use gpt-5.6-luna");
    }
    @Test void malformedResponseRecordsSafeFailureAndDoesNotRetry() {
        var builder=RestClient.builder().baseUrl("https://api.openai.com");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("secret-response-not-json",MediaType.APPLICATION_JSON));
        var result=new QaPersonaClient(spec(),"secret-key",builder.build())
                .next(new QaRunner.PersonaInput(spec().persona(),1,List.of()));
        assertThat(result.error()).contains("no automatic retry");
        assertThat(result.diagnostics()).extracting(QaRunner.Diagnostic::phase)
                .contains("REQUEST_STARTED","RESPONSE_RECEIVED");
        assertThat(result.diagnostics().getLast().exceptionType()).isNotBlank();
        assertThat(result.diagnostics()).allSatisfy(d->assertThat(java.time.Instant.parse(d.at())).isNotNull());
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("secret-key","secret-response-not-json");
        server.verify();
    }
}
