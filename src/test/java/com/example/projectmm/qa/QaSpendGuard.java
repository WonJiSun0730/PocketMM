package com.example.projectmm.qa;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** QA transport only: bounded outputs and conservative pre-request reservation across all roles. */
final class QaSpendGuard implements ClientHttpRequestInterceptor {
    private final QaSpec spec;
    private final ObjectMapper mapper=new ObjectMapper();
    private BigDecimal spent=BigDecimal.ZERO;
    private boolean uncertain;
    QaSpendGuard(QaSpec spec) {this.spec=spec;}
    synchronized void begin(QaRunner.State state) {spent=state.knownUsd();uncertain=state.unknownCost();}
    @Override public synchronized ClientHttpResponse intercept(HttpRequest request,byte[] body,ClientHttpRequestExecution execution) throws IOException {
        if(!spec.paid())throw new IllegalStateException("Mock run must never reach HTTP");
        if(uncertain)throw new IllegalStateException("Unknown prior charge; further API calls blocked");
        var json=mapper.readTree(body);
        var bounded=new LinkedHashMap<String,Object>();
        json.properties().forEach(e->bounded.put(e.getKey(),mapper.convertValue(e.getValue(),Object.class)));
        int limit=Integer.getInteger("pocket.qa.target-max-output",2048);
        if(limit<16||limit>8192)throw new IllegalArgumentException("QA target output limit must be 16..8192");
        long configured=json.path("max_output_tokens").asLong(limit);
        long output=Math.min(configured,limit);
        bounded.put("max_output_tokens",output);
        byte[] payload=mapper.writeValueAsBytes(bounded);
        var rate=spec.prices().get(json.path("model").asText());
        if(rate==null)throw new IllegalStateException("No configured model price");
        // UTF-8 payload bytes + framing allowance intentionally overestimate text-token input.
        long input=payload.length+2048L;
        var highest=rate.input().max(rate.cached()).max(rate.written());
        var im=rate.largeContextThreshold()>0&&input>rate.largeContextThreshold()?rate.largeInputMultiplier().max(BigDecimal.ONE):BigDecimal.ONE;
        var om=rate.largeContextThreshold()>0&&input>rate.largeContextThreshold()?rate.largeOutputMultiplier().max(BigDecimal.ONE):BigDecimal.ONE;
        var reservation=highest.multiply(BigDecimal.valueOf(input)).multiply(im)
                .add(rate.output().multiply(BigDecimal.valueOf(output)).multiply(om)).movePointLeft(6);
        if(spent.add(reservation).compareTo(spec.budgetUsd())>0)throw new IllegalStateException("API request blocked by conservative budget reservation");
        try {
            request.getHeaders().setContentLength(payload.length);
            var response=execution.execute(request,payload);
            byte[] bytes=response.getBody().readAllBytes();
            BigDecimal actual=null;
            try {
                var u=mapper.readTree(bytes).path("usage");var d=u.path("input_tokens_details");
                Long write=d.path("cache_write_tokens").isIntegralNumber()?d.path("cache_write_tokens").asLong():null;
                actual=rate.cost(u.path("input_tokens").asLong(-1),d.path("cached_tokens").asLong(-1),write,u.path("output_tokens").asLong(-1));
            } catch(RuntimeException ignored) { /* Unknown is never recorded as free. */ }
            if(actual==null) {uncertain=true;spent=spent.add(reservation);} else spent=spent.add(actual);
            return new ClientHttpResponse() {
                public HttpStatusCode getStatusCode() throws IOException {return response.getStatusCode();}
                public String getStatusText() throws IOException {return response.getStatusText();}
                public HttpHeaders getHeaders() {return response.getHeaders();}
                public InputStream getBody() {return new ByteArrayInputStream(bytes);}
                public void close() {response.close();}
            };
        } catch(IOException|RuntimeException ex) {uncertain=true;spent=spent.add(reservation);throw ex;}
    }
}
