package com.example.projectmm.infrastructure.ai;

import com.example.projectmm.domain.chat.WorldRoomSessionRepository;
import com.example.projectmm.infrastructure.dialogue.AiUsageTracker;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Routes auxiliary text at execution time. Never falls back across providers. */
@Component
public class TextProviderRouting {
    private final WorldRoomSessionRepository sessions;
    private final Environment env;
    private final ObjectMapper mapper;
    private final AiUsageTracker usage;
    private final RestClient client;

    public TextProviderRouting(WorldRoomSessionRepository sessions, Environment env, ObjectMapper mapper, AiUsageTracker usage) {
        this.sessions=sessions; this.env=env; this.mapper=mapper; this.usage=usage;
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        client=RestClient.builder().baseUrl("https://api.openai.com").requestFactory(factory).build();
    }

    public boolean openAi() {
        Long id=AiUsageTracker.currentSessionId();
        return id!=null && sessions.findById(id).map(s->"OPENAI".equalsIgnoreCase(s.getAiProvider()))
                .orElseThrow(()->new IllegalStateException("Text provider cannot be resolved for pocket "+id));
    }

    public String model(String purpose) {
        return env.getProperty("openai."+switch(purpose) {
            case "TIME_INTERPRETATION" -> "time-model";
            case "MEMORY_COMPRESSION" -> "compression-model";
            case "MEMORY_EMBEDDING" -> "embedding-model";
            default -> "memory-model";
        }, purpose.equals("MEMORY_EMBEDDING")?"text-embedding-3-small":"gpt-5.6-luna");
    }

    private String key() {
        String key=env.getProperty("openai.api-key", "");
        if(key.isBlank())throw new IllegalStateException("OPENAI selected but OpenAI API key is missing; Gemini fallback is blocked");
        return key;
    }

    /** Reuses the established prompts and parsers without sending a request to Google. */
    public String text(Map<String,Object> template,String purpose) {
        var node=mapper.valueToTree(template);
        var request=new LinkedHashMap<String,Object>();
        String selected=model(purpose);
        request.put("model",selected);request.put("store",false);
        String instructions=parts(node.path("systemInstruction").path("parts"));
        if(!instructions.isBlank())request.put("instructions",instructions);
        var input=new ArrayList<Map<String,Object>>();
        for(var content:node.path("contents"))input.add(Map.of("role","model".equals(content.path("role").asText())?"assistant":"user",
                "content",parts(content.path("parts"))));
        request.put("input",input);request.put("max_output_tokens",purpose.equals("MEMORY_COMPRESSION")?2048:4096);
        request.put("reasoning",Map.of("effort","none"));
        if("application/json".equals(node.path("generationConfig").path("responseMimeType").asText()))
            request.put("text",Map.of("format",Map.of("type","json_object")));
        return AiUsageTracker.withPurpose(AiUsageTracker.currentSessionId(),purpose,()->{
            String response=usage.accountRequest(AiUsageTracker.currentSessionId(),"OPENAI",selected,()->client.post().uri("/v1/responses")
                    .headers(h->h.setBearerAuth(key())).body(request).retrieve().body(String.class));
            var root=mapper.readTree(response);record(root,selected);
            if(!"completed".equals(root.path("status").asText()))throw new IllegalStateException("Incomplete OpenAI auxiliary response");
            var text=new StringBuilder();
            for(var output:root.path("output"))for(var part:output.path("content")) {
                if("refusal".equals(part.path("type").asText()))throw new IllegalStateException("OpenAI auxiliary request refused");
                if("output_text".equals(part.path("type").asText()))text.append(part.path("text").asText());
            }
            if(text.isEmpty())throw new IllegalStateException("Missing OpenAI auxiliary output");
            return mapper.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",text.toString())))))));
        });
    }

    public List<Double> embed(String text) {
        String selected=model("MEMORY_EMBEDDING");
        return AiUsageTracker.withPurpose(AiUsageTracker.currentSessionId(),"MEMORY_EMBEDDING",()->{
            String response=usage.accountRequest(AiUsageTracker.currentSessionId(),"OPENAI",selected,()->client.post().uri("/v1/embeddings")
                    .headers(h->h.setBearerAuth(key())).body(Map.of("model",selected,"input",text,"encoding_format","float"))
                    .retrieve().body(String.class));
            var root=mapper.readTree(response);record(root,selected);
            var vector=root.path("data").path(0).path("embedding");
            if(!vector.isArray()||vector.isEmpty()||vector.valueStream().anyMatch(n->!n.isNumber()||!Double.isFinite(n.asDouble())))
                throw new IllegalStateException("Invalid OpenAI embedding response");
            return vector.valueStream().map(n->n.asDouble()).toList();
        });
    }

    private void record(tools.jackson.databind.JsonNode root,String model) {
        var u=root.path("usage");
        if(!u.isObject()) {usage.recordUnavailable(AiUsageTracker.currentSessionId(),"OPENAI",model,"USAGE_NOT_REPORTED");return;}
        long input=u.path("input_tokens").asLong(u.path("prompt_tokens").asLong(0));
        usage.record(AiUsageTracker.currentSessionId(),"OPENAI",model,input,u.path("input_tokens_details").path("cached_tokens").asLong(0),
                u.has("prompt_tokens")?Long.valueOf(0):u.path("input_tokens_details").path("cache_write_tokens").isIntegralNumber()?Long.valueOf(u.path("input_tokens_details").path("cache_write_tokens").asLong()):null,u.path("output_tokens").asLong(0),u.path("total_tokens").asLong(input));
    }
    private String parts(tools.jackson.databind.JsonNode parts) {
        var text=new StringBuilder();for(var part:parts)text.append(part.path("text").asText()).append('\n');return text.toString();
    }
}
