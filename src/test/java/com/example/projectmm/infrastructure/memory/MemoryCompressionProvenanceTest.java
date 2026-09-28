package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.MemoryCompressorOutput;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.projectmm.infrastructure.memory.MemoryCompressionPromptBuilderTest.memory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MemoryCompressionProvenanceTest {
    private final MemoryAccessStateRepository access = mock(MemoryAccessStateRepository.class);
    private final MemoryCompressionPromptBuilder prompts = new MemoryCompressionPromptBuilder(access);

    @Test
    void successfulPatternReturnsOnlyFilteredPromptEvidence() {
        var source = memory(1, "event");
        source.compress("summary");
        var secret = memory(2, "secret");
        when(access.findByMemoryId(2L)).thenReturn(Optional.of(
                new MemoryAccessState(secret, MemoryAccessLevel.BLOCKED, LocalDateTime.now())));
        var output = compress(source, MemoryDetailLevel.PATTERN,
                List.of(source, secret, memory(3, "other event"), memory(3, "duplicate")), "pattern");
        assertThat(output).isEqualTo(new MemoryCompressorOutput("pattern", List.of(3L)));
        assertThat(source.getSummary()).isEqualTo("summary");
        assertThat(source.getImportance()).isEqualTo(5);
        assertThat(source.getNarrativeAnchor()).isZero();
    }

    @Test
    void schemaReturnsMultipleEvidenceIdsAndSummaryReturnsNone() {
        var source = memory(1, "event");
        assertThat(compress(source, MemoryDetailLevel.SUMMARY, List.of(memory(2, "unused")), "summary")
                .evidenceMemoryIds()).isEmpty();
        source.compress("summary");
        source.compress("pattern");
        assertThat(compress(source, MemoryDetailLevel.SCHEMA,
                List.of(memory(2, "a"), memory(3, "b")), "schema").evidenceMemoryIds())
                .containsExactly(2L, 3L);
    }

    @Test
    void invalidOutputReturnsNoEvidenceResult() {
        var source = memory(1, "event");
        source.compress("summary");
        for (String invalid : List.of("", " ", "x".repeat(501))) {
            assertThat(compress(source, MemoryDetailLevel.PATTERN, List.of(memory(2, "evidence")), invalid)).isNull();
        }
        assertThat(source.getDetailLevel()).isEqualTo(MemoryDetailLevel.SUMMARY);
    }

    @Test
    void evidenceIdsAreImmutableDistinctAndValidated() {
        var ids = new java.util.ArrayList<>(List.of(2L, 2L, 3L));
        var output = new MemoryCompressorOutput("pattern", ids);
        ids.clear();
        assertThat(output.evidenceMemoryIds()).containsExactly(2L, 3L);
        assertThatThrownBy(() -> output.evidenceMemoryIds().add(4L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new MemoryCompressorOutput("pattern", List.of(0L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MemoryCompressorOutput compress(Memory source, MemoryDetailLevel target, List<Memory> candidates, String text) {
        var client = RestClient.builder();
        var server = MockRestServiceServer.bindTo(client).build();
        var mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                "content", Map.of("parts", List.of(Map.of("text", mapper.writeValueAsString(Map.of("summary", text)))))))));
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var output = new GeminiMemoryCompressor("fake", "fake", mapper, prompts, client).compress(source, target, candidates);
        server.verify();
        return output;
    }
}
