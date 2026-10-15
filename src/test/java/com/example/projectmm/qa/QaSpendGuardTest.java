package com.example.projectmm.qa;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class QaSpendGuardTest {
    static QaSpec paid(String budget) {
        var m=QaSpec.mock("guard",20);var rate=new QaSpec.Rate(BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,0,BigDecimal.ONE,BigDecimal.ONE,"fixture","2026-10-15");
        return new QaSpec("guard",20,true,"guard",m.persona(),"gpt-5.6-luna","model","model",new BigDecimal(budget),new BigDecimal("0.001"),Map.of("model",rate,"gpt-5.6-luna",rate),3,true);
    }
    @Test void insufficientBudgetBlocksBeforeNetwork() {
        var guard=new QaSpendGuard(paid("0.00001"));var builder=RestClient.builder().baseUrl("https://api.openai.com").requestInterceptor(guard);
        var server=MockRestServiceServer.bindTo(builder).build();var client=builder.build();
        assertThatThrownBy(()->client.post().uri("/v1/responses").body(Map.of("model","model","input","hello")).retrieve().body(String.class))
                .hasMessageContaining("budget reservation");server.verify();
    }
    @Test void outputIsBoundedAndResponseRemainsReadable() {
        var guard=new QaSpendGuard(paid("1"));var builder=RestClient.builder().baseUrl("https://api.openai.com").requestInterceptor(guard);
        var server=MockRestServiceServer.bindTo(builder).build();
        String body="{\"status\":\"completed\",\"usage\":{\"input_tokens\":20,\"input_tokens_details\":{\"cached_tokens\":0,\"cache_write_tokens\":0},\"output_tokens\":10}}";
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(content().string(containsString("\"max_output_tokens\":2048")))
                .andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThat(builder.build().post().uri("/v1/responses").body(Map.of("model","model","input","hello")).retrieve().body(String.class)).isEqualTo(body);
        server.verify();
    }
    @Test void unknownChargeBlocksNextCallEvenInsideSameTurn() {
        var guard=new QaSpendGuard(paid("1"));var builder=RestClient.builder().baseUrl("https://api.openai.com").requestInterceptor(guard);
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("{\"status\":\"completed\"}",MediaType.APPLICATION_JSON));
        var client=builder.build();client.post().uri("/v1/responses").body(Map.of("model","model","input","hello")).retrieve().body(String.class);
        assertThatThrownBy(()->client.post().uri("/v1/responses").body(Map.of("model","model","input","again")).retrieve().body(String.class))
                .hasMessageContaining("Unknown prior charge");server.verify();
    }
}
