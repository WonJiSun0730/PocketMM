package com.example.projectmm.infrastructure.memory;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class OpenAiChatMemoryExtractorTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final OpenAiChatMemoryExtractor extractor=new OpenAiChatMemoryExtractor("test", "gpt-5.6-luna", mapper);
    private String response(String status,String json) {
        return mapper.writeValueAsString(Map.of("status",status,"output",List.of(Map.of("type","message","content",
                List.of(Map.of("type","output_text","text",json))))));
    }
    @Test void usesIdenticalEvidenceInstructionsAndStrictSchemaWithoutPaidCall() {
        var policy=new GeminiChatMemoryExtractor("test","test",mapper);
        var request=mapper.valueToTree(extractor.createRequest("나는 소총의 소유권을 넘기지 않는다.",true));
        var existing=mapper.valueToTree(policy.createNarrativeRequest("나는 소총의 소유권을 넘기지 않는다."));
        assertThat(request.path("instructions").asText()).isEqualTo(existing.path("systemInstruction").path("parts").path(0).path("text").asText());
        assertThat(request.path("store").asBoolean()).isFalse();
        var schema=request.path("text").path("format").path("schema");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        var semantic=schema.path("properties").path("memories").path("items").path("properties").path("semanticMemory");
        assertThat(semantic.path("properties").path("type").path("type").asText()).isEqualTo("string");
        assertThat(semantic.path("type").get(1).asText()).isEqualTo("null");
        assertThat(semantic.path("required").size()).isEqualTo(7);
    }
    @Test void existingParserPreservesQuotesAndRejectsIncompleteResponses() {
        String json="""
                {"memories":[{"quote":"나는 소총의 소유권을 넘기지 않는다.","importance":6,"fact":null,"details":[]}]}
                """;
        var result=extractor.parseResponse(response("completed",json));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().quote()).isEqualTo("나는 소총의 소유권을 넘기지 않는다.");
        assertThatThrownBy(()->extractor.parseResponse(response("incomplete",mapper.writeValueAsString(Map.of("memories",List.of()))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->extractor.parseResponse(response("completed","{}"))).isInstanceOf(IllegalArgumentException.class);
    }
}
