package com.example.projectmm.qa;
import tools.jackson.databind.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;
import java.util.*;

/** Provider tokenization only. Never falls back to a characters/bytes heuristic. */
final class ExperienceInputCounter {
    private static final ObjectMapper M=new ObjectMapper();
    private static String safe(String value) {
        return value!=null&&value.matches("[a-zA-Z0-9_.-]{1,80}")?value:"unknown";
    }
    private static final class TextClient {
        static final RestClient INSTANCE=createClient();
        private static RestClient createClient() {
            var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15)).build());
            factory.setReadTimeout(java.time.Duration.ofSeconds(30));
            return RestClient.builder().requestFactory(factory).build();
        }
    }
    private static final class EmbeddingTokenizer {
        static final com.knuddels.jtokkit.api.Encoding INSTANCE=com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry()
                .getEncoding(com.knuddels.jtokkit.api.EncodingType.CL100K_BASE);
    }
    static long count(String provider,String model,JsonNode request,boolean embedding) {
        if(!provider.equals("OPENAI"))throw new IllegalStateException("OpenAI QA blocks other provider token counters before HTTP");
        return count(provider,model,request,embedding,embedding?null:TextClient.INSTANCE,embedding?null:ExperienceEnvironment.key());
    }
    static long count(String provider,String model,JsonNode request,boolean embedding,RestClient client,String key) {
        if(!provider.equals("OPENAI"))throw new IllegalStateException("OpenAI QA blocks other provider token counters before HTTP");
        if(embedding) {
            if(!java.util.Set.of("text-embedding-3-small","text-embedding-3-large").contains(model) || !request.path("input").isString())
                throw new IllegalStateException("Verified embedding tokenizer unavailable; request blocked");
            return EmbeddingTokenizer.INSTANCE.countTokens(request.path("input").asText());
        }
        String response;
        {
            // Token counting accepts input-shaping fields, not generation controls.
            var countRequest=M.createObjectNode();
            for(String field:List.of("model","input","instructions","reasoning","text",
                    "tools","tool_choice","parallel_tool_calls","truncation")) {
                if(request.has(field))countRequest.set(field,request.get(field));
            }
            try {
                response=client.post().uri("https://api.openai.com/v1/responses/input_tokens")
                        .headers(h->h.setBearerAuth(key)).contentType(MediaType.APPLICATION_JSON)
                        .body(countRequest).retrieve().body(String.class);
            } catch(RestClientResponseException e) {
                // Do not log the raw provider response or prompt.
                String code="unknown",param="unknown",type="unknown";
                try {
                    var error=M.readTree(e.getResponseBodyAsString()).path("error");
                    code=error.path("code").asText("unknown");
                    param=error.path("param").asText("unknown");
                    type=error.path("type").asText("unknown");
                } catch(RuntimeException ignored) {}
                throw new IllegalStateException("OpenAI token count HTTP "+e.getStatusCode().value()
                        +" (type="+safe(type)+", code="+safe(code)+", param="+safe(param)+")");
            }
        }
        JsonNode count=M.readTree(response).path("input_tokens");
        if(!count.isIntegralNumber()||!count.canConvertToLong()||count.asLong()<0)throw new IllegalStateException("Provider input count unavailable");
        return count.asLong();
    }
}

