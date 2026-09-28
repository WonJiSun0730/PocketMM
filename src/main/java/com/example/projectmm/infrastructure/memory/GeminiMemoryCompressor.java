package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.MemoryCompressor;
import com.example.projectmm.application.memory.MemoryCompressorOutput;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Profile("gemini")
public class GeminiMemoryCompressor implements MemoryCompressor {
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final MemoryCompressionPromptBuilder prompts;
    private final RestClient client;

    @Autowired
    public GeminiMemoryCompressor(@Value("${gemini.api-key}") String apiKey,
                                  @Value("${gemini.compression-model:${gemini.memory-model:gemini-3.5-flash-lite}}") String model,
                                  ObjectMapper mapper, MemoryCompressionPromptBuilder prompts) {
        this(apiKey, model, mapper, prompts, clientBuilder());
    }

    GeminiMemoryCompressor(String apiKey, String model, ObjectMapper mapper,
                           MemoryCompressionPromptBuilder prompts, RestClient.Builder builder) {
        this.apiKey = apiKey;
        this.model = model;
        this.mapper = mapper;
        this.prompts = prompts;
        this.client = builder.baseUrl("https://generativelanguage.googleapis.com").build();
    }

    private static RestClient.Builder clientBuilder() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public MemoryCompressorOutput compress(Memory memory, MemoryDetailLevel targetLevel, List<Memory> relatedMemories) {
        var prompt = prompts.prepare(memory, targetLevel, relatedMemories);
        if (prompt.isEmpty() || apiKey == null || apiKey.isBlank()) return null;
        try {
            var schema = Map.of("type", "object", "required", List.of("summary"),
                    "properties", Map.of("summary", Map.of("type", List.of("string", "null"))));
            var request = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt.get().text())))),
                    "generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", schema));
            String response = client.post().uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey).body(request).retrieve().body(String.class);
            String summary = parseResponse(response);
            return summary == null ? null : new MemoryCompressorOutput(summary, prompt.get().evidenceMemoryIds());
        } catch (RuntimeException ex) {
            // 실패한 압축으로 원문/단계를 바꾸거나 추가 유료 재시도를 하지 않는다.
            return null;
        }
    }

    String parseResponse(String response) {
        var candidate = mapper.readTree(response).path("candidates").path(0);
        if (!"STOP".equals(candidate.path("finishReason").asText())) return null;
        StringBuilder text = new StringBuilder();
        for (var part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
        }
        var summary = mapper.readTree(text.toString()).path("summary");
        if (!summary.isString() || summary.asText().isBlank() || summary.asText().length() > 500) return null;
        return summary.asText().strip();
    }
}
