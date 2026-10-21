package com.example.projectmm.infrastructure.memory;

import com.example.projectmm.application.memory.ChatMemoryExtractor;
import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** Uses the existing evidence policy and candidate parser; one extraction request, no retries. */
public class OpenAiChatMemoryExtractor implements ChatMemoryExtractor {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.example.projectmm.infrastructure.dialogue.AiUsageTracker usageTracker = new com.example.projectmm.infrastructure.dialogue.AiUsageTracker();
    public void setUsageTracker(com.example.projectmm.infrastructure.dialogue.AiUsageTracker tracker) {this.usageTracker=tracker;}
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;
    private final GeminiChatMemoryExtractor policy;
    private final RestClient client;

    public OpenAiChatMemoryExtractor(String apiKey,String model,ObjectMapper mapper) {
        this.apiKey=apiKey; this.model=model; this.mapper=mapper;
        policy=new GeminiChatMemoryExtractor("", "", mapper);
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        client=RestClient.builder().baseUrl("https://api.openai.com").requestFactory(factory).build();
    }

    @Override public List<ChatMemoryCandidate> extract(String text) { return extract(text,false); }
    @Override public List<ChatMemoryCandidate> extractNarrative(String text) { return extract(text,true); }
    private List<ChatMemoryCandidate> extract(String text,boolean narrative) {
        if(apiKey==null||apiKey.isBlank())throw new IllegalStateException("Selected memory provider API key is missing; fallback blocked");
        String response=usageTracker.accountRequest(com.example.projectmm.infrastructure.dialogue.AiUsageTracker.currentSessionId(),"OPENAI",model, () -> client.post().uri("/v1/responses").headers(h->h.setBearerAuth(apiKey))
                .body(createRequest(text,narrative)).retrieve().body(String.class));
        recordUsage(response);
        return parseResponse(response);
    }

    void recordUsage(String response) {
        var usage=mapper.readTree(response).path("usage");
        if(!usage.isObject()) {
            usageTracker.recordUnavailable(com.example.projectmm.infrastructure.dialogue.AiUsageTracker.currentSessionId(),"OPENAI",model,"USAGE_NOT_REPORTED");return;
        }
        var details=usage.path("input_tokens_details");
        usageTracker.record(com.example.projectmm.infrastructure.dialogue.AiUsageTracker.currentSessionId(),
                "OPENAI",model,usage.path("input_tokens").asLong(0),details.path("cached_tokens").asLong(0),
                details.path("cache_write_tokens").isIntegralNumber()?details.path("cache_write_tokens").asLong():null,
                usage.path("output_tokens").asLong(0),usage.path("total_tokens").asLong(0));
    }

    Map<String,Object> createRequest(String text,boolean narrative) {
        var template=mapper.valueToTree(narrative?policy.createNarrativeRequest(text):policy.createRequest(text));
        var request=new LinkedHashMap<String,Object>();
        request.put("model",model);
        request.put("store",false);
        request.put("instructions",template.path("systemInstruction").path("parts").path(0).path("text").asText());
        request.put("input",List.of(Map.of("role","user","content",text)));
        request.put("max_output_tokens",2048);
        request.put("reasoning",Map.of("effort","none"));
        request.put("text",Map.of("format",Map.of("type","json_schema","name","memory_candidates","strict",true,
                "schema",toOpenAiSchema(template.path("generationConfig").path("responseSchema")))));
        return request;
    }

    private Object toOpenAiSchema(JsonNode node) {
        if(node.isObject()) {
            var result=new LinkedHashMap<String,Object>();
            node.properties().forEach(entry->{
                String key=entry.getKey();
                if("nullable".equals(key)||"required".equals(key)) return;
                result.put(key,"type".equals(key) && entry.getValue().isString()?entry.getValue().asText().toLowerCase(Locale.ROOT):toOpenAiSchema(entry.getValue()));
            });
            if(node.path("nullable").asBoolean(false)) result.put("type",List.of(result.get("type"),"null"));
            if(node.has("properties")) {
                result.put("additionalProperties",false);
                result.put("required",node.path("properties").properties().stream().map(Map.Entry::getKey).toList());
            }
            return result;
        }
        if(node.isArray()) {
            var items=new ArrayList<Object>();
            for(var item:node) items.add(toOpenAiSchema(item));
            return items;
        }
        if(node.isString()) return node.asText();
        if(node.isBoolean()) return node.asBoolean();
        if(node.isIntegralNumber()) return node.asLong();
        throw new IllegalArgumentException("Unsupported memory schema value");
    }

    List<ChatMemoryCandidate> parseResponse(String response) {
        var root=mapper.readTree(response);
        if(!"completed".equals(root.path("status").asText())) throw new IllegalArgumentException("Incomplete memory extraction");
        var json=new StringBuilder();
        for(var item:root.path("output")) {
            if(!"message".equals(item.path("type").asText())) continue;
            for(var part:item.path("content")) {
                if("refusal".equals(part.path("type").asText())) throw new IllegalArgumentException("Memory extraction refused");
                if("output_text".equals(part.path("type").asText())) json.append(part.path("text").asText());
            }
        }
        if(json.isEmpty()) throw new IllegalArgumentException("Missing memory extraction output");
        return policy.parseCandidates(json.toString());
    }
}
