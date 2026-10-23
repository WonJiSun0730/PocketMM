package com.example.projectmm.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ExperienceInputCounterTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void reusedClientStillCountsEveryRequestWithItsOwnCredentialsAndInput() {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        for(int i=1;i<=2;i++) {
            server.expect(requestTo("https://api.openai.com/v1/responses/input_tokens"))
                    .andExpect(header("Authorization","Bearer key-"+i))
                    .andExpect(content().json("{\"model\":\"target\",\"input\":\"turn-"+i+"\",\"instructions\":\"rules\"}",true))
                    .andRespond(withSuccess("{\"input_tokens\":"+i+"}",MediaType.APPLICATION_JSON));
        }
        var client=builder.build();
        for(int i=1;i<=2;i++) {
            var request=mapper.readTree("{\"model\":\"target\",\"input\":\"turn-"+i+"\",\"instructions\":\"rules\",\"max_output_tokens\":20000,\"store\":false}");
            assertThat(ExperienceInputCounter.count("OPENAI","target",request,false,client,"key-"+i)).isEqualTo(i);
        }
        server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","null","\"12\""})
    void invalidProviderCountStillBlocks(String count) {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{\"input_tokens\":"+count+"}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->ExperienceInputCounter.count("OPENAI","target",mapper.createObjectNode(),false,builder.build(),"key"))
                .hasMessageContaining("Provider input count unavailable");
        server.verify();
    }
    @Test void embeddingTokenizerIsOfflineAndRejectsUnsupportedModels() {
        var input=mapper.createObjectNode().put("input","hello world");
        assertThat(ExperienceInputCounter.count("OPENAI","text-embedding-3-small",input,true)).isEqualTo(2);
        assertThat(ExperienceInputCounter.count("OPENAI","text-embedding-3-small",input,true)).isEqualTo(2);
        assertThatThrownBy(()->ExperienceInputCounter.count("OPENAI","unknown",input,true)).hasMessageContaining("blocked");
    }
}
