package com.example.projectmm.qa;
import tools.jackson.databind.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import java.util.*;

/** Provider tokenization only. Never falls back to a characters/bytes heuristic. */
final class ExperienceInputCounter {
    private static final ObjectMapper M=new ObjectMapper();
    static long count(String provider,String model,JsonNode request,boolean embedding) {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        RestClient client=RestClient.builder().requestFactory(factory).build();
        String response;
        if(provider.equals("OPENAI")) {
            response=client.post().uri("https://api.openai.com/v1/responses/input_tokens")
                    .headers(h->h.setBearerAuth(ExperienceEnvironment.key())).contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(String.class);
        } else {
            var payload=M.createObjectNode();
            if(embedding)payload.putArray("contents").add(request.path("content"));
            else {var content=(tools.jackson.databind.node.ObjectNode)request.deepCopy();content.put("model","models/"+model);payload.set("generateContentRequest",content);}
            response=client.post().uri("https://generativelanguage.googleapis.com/v1beta/models/"+model+":countTokens")
                    .header("x-goog-api-key",ExperienceEnvironment.geminiKey()).contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(String.class);
        }
        JsonNode count=M.readTree(response).path(provider.equals("OPENAI")?"input_tokens":"totalTokens");
        if(!count.isIntegralNumber()||!count.canConvertToLong()||count.asLong()<0)throw new IllegalStateException("Provider input count unavailable");
        return count.asLong();
    }
}
