package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.*;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.mockito.Mockito.*;
import static com.example.projectmm.infrastructure.memory.MemoryCompressionPromptBuilderTest.memory;

class GeminiMemoryCompressorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MemoryCompressionPromptBuilder prompts = new MemoryCompressionPromptBuilder(mock(MemoryAccessStateRepository.class));

    @Test
    void fakeHttpResponseProducesSummaryWithoutTouchingOriginal() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var compressor = new GeminiMemoryCompressor("test", "test", mapper, prompts, builder);
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/test:generateContent"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Target: SUMMARY")))
                .andRespond(withSuccess(envelope("{\"summary\":\"핵심 사건\"}", "STOP"), MediaType.APPLICATION_JSON));
        var source = memory(1, "original");
        assertThat(compressor.compress(source, MemoryDetailLevel.SUMMARY, List.of())).isEqualTo("핵심 사건");
        assertThat(source.getSummary()).isEqualTo("original");
        assertThat(source.getDetailLevel()).isEqualTo(MemoryDetailLevel.DETAILED);
        server.verify();
    }

    @Test
    void insufficientEvidenceMakesNoHttpRequest() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var compressor = new GeminiMemoryCompressor("test", "test", mapper, prompts, builder);
        var source = memory(1, "event");
        source.compress("event");
        assertThat(compressor.compress(source, MemoryDetailLevel.PATTERN, List.of())).isNull();
        server.verify();
    }

    @Test
    void httpFailureAbstainsWithoutRetry() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var compressor = new GeminiMemoryCompressor("test", "test", mapper, prompts, builder);
        server.expect(anything()).andRespond(withServerError());
        assertThat(compressor.compress(memory(1, "event"), MemoryDetailLevel.SUMMARY, List.of())).isNull();
        server.verify();
    }

    @Test
    void invalidOrTruncatedOutputDoesNotBecomeSummary() {
        var compressor = new GeminiMemoryCompressor("test", "test", mapper, prompts, RestClient.builder());
        for (String payload : List.of("{\"summary\":null}", "{\"summary\":42}", "{\"summary\":\" \"}",
                mapper.writeValueAsString(Map.of("summary", "x".repeat(501))))) {
            assertThat(compressor.parseResponse(envelope(payload, "STOP"))).isNull();
        }
        assertThat(compressor.parseResponse(envelope("{\"summary\":\"text\"}", "MAX_TOKENS"))).isNull();
    }

    @Test
    void profilesSelectExactlyOneCompressorAndFallbackRemainsConservative() {
        var context = new ApplicationContextRunner()
                .withBean(ObjectMapper.class, () -> mapper)
                .withBean(MemoryAccessStateRepository.class, () -> mock(MemoryAccessStateRepository.class))
                .withUserConfiguration(MemoryCompressionPromptBuilder.class, RuleBasedMemoryCompressor.class, GeminiMemoryCompressor.class)
                .withPropertyValues("gemini.api-key=test");
        context.run(app -> {
            assertThat(app).hasSingleBean(MemoryCompressor.class);
            assertThat(app.getBean(MemoryCompressor.class)).isInstanceOf(RuleBasedMemoryCompressor.class);
            assertThat(app.getBean(MemoryCompressor.class).compress(memory(1, "unchanged"), MemoryDetailLevel.SUMMARY, List.of()))
                    .isEqualTo("unchanged");
        });
        context.withPropertyValues("spring.profiles.active=gemini").run(app -> {
            assertThat(app).hasSingleBean(MemoryCompressor.class);
            assertThat(app.getBean(MemoryCompressor.class)).isInstanceOf(GeminiMemoryCompressor.class);
        });
    }

    private String envelope(String text, String finish) {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", finish,
                "content", Map.of("parts", List.of(Map.of("thought", true, "text", "private"), Map.of("text", text)))))));
    }
}
