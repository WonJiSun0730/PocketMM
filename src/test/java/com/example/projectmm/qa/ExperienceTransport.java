package com.example.projectmm.qa;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.http.*;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Experience-only hard cap. Prompts unchanged; role output caps and reservations are durable. */
final class ExperienceTransport implements ClientHttpRequestInterceptor {
    record Receipt(String callId,String at,String phase,String role,String provider,String model,String purpose,long input,long cached,
                   Long written,long output,String status,BigDecimal usd,String exceptionType) {
        QaRunner.Usage usage(){return new QaRunner.Usage(role,model,purpose,input,cached,written,output,status,usd,callId,0);}
    }
    private final QaSpec spec;private final Path directory;private final ObjectMapper mapper=new ObjectMapper();
    private final Map<String,Long> maxima;private BigDecimal spent=BigDecimal.ZERO;private boolean uncertain;
    boolean budgetBlocked;
    private String blockedReason,requestRole="AUXILIARY",requestModel="unknown";
    interface InputCounter {long count(String provider,String model,tools.jackson.databind.JsonNode request,boolean embedding);}
    private final InputCounter counter;
    private final tools.jackson.databind.JsonNode policy;
    private BigDecimal reserved=BigDecimal.ZERO;
    private final LinkedHashMap<String,Object> budget=new LinkedHashMap<>();
    private void budgetEvent(String phase,String reason) throws IOException {
        budget.put("phase",phase);budget.put("reason",reason);budget.put("at",Instant.now().toString());
        budget.put("budgetUsd",spec.budgetUsd());budget.put("actualUsd",spent);budget.put("reservedUsd",reserved);
        budget.put("remainingUsd",spec.budgetUsd().subtract(spent).subtract(reserved));
        budget.put("uncertain",uncertain);
        byte[] bytes=(mapper.writeValueAsString(budget)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var c=FileChannel.open(directory.resolve("budget-events.jsonl"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)) {
            var b=java.nio.ByteBuffer.wrap(bytes);while(b.hasRemaining())c.write(b);c.force(true);
        }
        Path temp=Files.createTempFile(directory,"budget-",".tmp");
        try {
            Files.write(temp,bytes);
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            QaAtomicFiles.replace(temp,directory.resolve("budget.json"));
        }
        finally {Files.deleteIfExists(temp);}
    }
    private void block(String reason) throws IOException {budgetBlocked=true;blockedReason=reason;budgetEvent("BLOCKED",reason);throw new IllegalStateException(reason);}

    private final LinkedHashMap<String,Receipt> calls=new LinkedHashMap<>();
    ExperienceTransport(QaSpec spec,Path directory) throws IOException {
        this(spec,directory,ExperienceInputCounter::count);
    }
    ExperienceTransport(QaSpec spec,Path directory,InputCounter counter) throws IOException {
        this.counter=counter;this.spec=spec;this.directory=directory;Files.createDirectories(directory.resolve("requests"));
        var manifest=mapper.readTree(Files.readString(directory.resolve("manifest.json")));maxima=new HashMap<>();policy=manifest.path("budgetPolicy");
        manifest.path("maximumOutputTokens").properties().forEach(e->maxima.put(e.getKey(),e.getValue().asLong()));
        Path ledger=directory.resolve("ledger.jsonl");
        if(Files.exists(ledger))for(String line:Files.readAllLines(ledger))if(!line.isBlank()) {
            var receipt=mapper.readValue(line,Receipt.class);calls.put(receipt.callId(),receipt);
        }
        for(var receipt:calls.values()){if(receipt.usd()==null)uncertain=true;else spent=spent.add(receipt.usd());}
        Path prior=directory.resolve("budget.json");
        if(Files.exists(prior)) {
            var saved=mapper.readTree(Files.readString(prior));reserved=saved.path("reservedUsd").decimalValue();
            if(reserved.signum()>0||saved.path("uncertain").asBoolean())uncertain=true;
        }
    }
    synchronized List<QaRunner.Usage> since(Set<String> before){return calls.values().stream().filter(c->!before.contains(c.callId())).map(Receipt::usage).toList();}
    synchronized Set<String> ids(){return Set.copyOf(calls.keySet());}
    synchronized QaRunner.Outcome outcomeSince(Set<String> before,QaRunner.Outcome outcome) {
        var receipts=new ArrayList<>(since(before));
        if(budgetBlocked)receipts.add(new QaRunner.Usage(requestRole,requestModel,"NOT_SENT",0,0,0L,0,"BUDGET_BLOCKED",BigDecimal.ZERO));
        return new QaRunner.Outcome(outcome.reply(),outcome.memories(),outcome.evaluationJson(),outcome.diagnosticsJson(),
                outcome.memoryBeforeJson(),outcome.memoryAfterJson(),receipts,
                budgetBlocked&&outcome.error()!=null?"요청 전송 전 차단: "+blockedReason:outcome.error());
    }
    synchronized void begin(QaRunner.State state){if(state.unknownCost())uncertain=true;}
    private void record(Receipt receipt) throws IOException {
        // No auth headers, URL query keys or exception text are written.
        byte[] line=(mapper.writeValueAsString(receipt)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var channel=FileChannel.open(directory.resolve("ledger.jsonl"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)) {
            var buffer=java.nio.ByteBuffer.wrap(line);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
        }
        calls.put(receipt.callId(),receipt);
    }
    private static boolean hasExternalContent(tools.jackson.databind.JsonNode node) {
        if(node.isObject()) {
            for(String key:List.of("file_id","file_url","image_url","inlineData","fileData","audio","videoMetadata","continuation_token"))if(node.has(key))return true;
            for(var entry:node.properties())if(hasExternalContent(entry.getValue()))return true;
        } else if(node.isArray())for(var child:node)if(hasExternalContent(child))return true;
        return false;
    }
    private static byte[] digest(byte[] body) {try{return java.security.MessageDigest.getInstance("SHA-256").digest(body);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    @Override public synchronized ClientHttpResponse intercept(HttpRequest request,byte[] body,ClientHttpRequestExecution execution) throws IOException {
        if(!spec.paid())throw new IllegalStateException("MOCK HTTP prohibited");
        if(budgetBlocked)throw new IllegalStateException("Budget policy already blocked this run; no further HTTP requests permitted");
        if(uncertain){budgetEvent("REVIEW_REQUIRED","Unknown prior charge; automatic retry prohibited");throw new IllegalStateException("Unknown prior charge; automatic retry prohibited");}
        var json=mapper.readTree(body);boolean gemini=request.getURI().getHost().equals("generativelanguage.googleapis.com");
        boolean embedding=request.getURI().getPath().equals("/v1/embeddings");
        String model=gemini?request.getURI().getPath().replaceFirst(".*/models/","").replaceFirst(":.*",""):json.path("model").asText();
        String provider=gemini?"GEMINI":"OPENAI";
        Object scope=((ThreadLocal<?>)org.springframework.test.util.ReflectionTestUtils.getField(com.example.projectmm.infrastructure.dialogue.AiUsageTracker.class,"scope")).get();
        String purpose=scope==null?"ROLEPLAY":(String)org.springframework.test.util.ReflectionTestUtils.getField(scope,"purpose");
        if(ExperiencePersonaClient.personaCall.get())purpose="PLAYER_SIMULATION";
        String role=ExperiencePersonaClient.personaCall.get()?"PERSONA":!"SCENE_CONTINUITY".equals(purpose)&&model.equals(spec.targetModel())&&!gemini&&json.path("text").path("format").path("name").asText().equals("pocket_turn")?"TARGET":"AUXILIARY";
        requestRole=role;requestModel=model;
        if(gemini)block("OpenAI QA에서 Gemini 요청을 전송 전에 차단했습니다. 새 실행을 만드세요.");
        var rate=spec.prices().get(model);Long maximum=maxima.get(model);
        if(rate==null||maximum==null||maximum<(embedding?0:1))throw new IllegalStateException("Verified model price and maximum output reservation required");
        budget.clear();budget.put("model",model);budget.put("role",role);budget.put("purpose",purpose);
        if(!policy.path("version").asText().equals(com.example.projectmm.application.qa.ExperienceBudgetPolicy.VERSION)) {
            block("엄격한 예산 정책이 없는 이전 실행입니다. 기록을 보존하고 새 테스트를 만드세요.");
        }
        // Only locally generated text calls are supported: no tools, remote content or multiple candidates.
        if(!request.getURI().getScheme().equals("https")||hasExternalContent(json)||!Set.of("api.openai.com","generativelanguage.googleapis.com").contains(request.getURI().getHost())
                ||json.has("tools")||json.has("previous_response_id")||json.has("conversation")||json.has("cachedContent")
                ||json.path("generationConfig").path("candidateCount").asInt(1)!=1
                ||(!embedding&&!gemini&&!request.getURI().getPath().equals("/v1/responses"))
                ||(gemini&&!embedding&&!request.getURI().getPath().endsWith(":generateContent")))
            block("최대 과금을 검증할 수 없는 요청 유형입니다. 전송하지 않았습니다.");
        String bucket=role.equals("TARGET")?"TARGET":role.equals("PERSONA")?"PLAYER_SIMULATION":
                policy.path("outputCaps").has(purpose)?purpose:"AUXILIARY";
        long cap=embedding?0:policy.path("outputCaps").path(bucket).asLong(-1);
        if(cap<0||(!embedding&&cap==0))block("역할별 출력 상한이 없어 전송하지 않았습니다.");
        var modified=(tools.jackson.databind.node.ObjectNode)json.deepCopy();
        long existing=gemini?json.path("generationConfig").path("maxOutputTokens").asLong(maximum):json.path("max_output_tokens").asLong(maximum);
        long output=embedding?0:Math.min(Math.min(existing,cap),maximum);
        if(!embedding&&output<1)block("유효한 출력 상한이 없어 전송하지 않았습니다.");
        if(!embedding) {
            if(gemini)modified.withObject("generationConfig").put("maxOutputTokens",output);
            else modified.put("max_output_tokens",output);
        }
        // Provider thinking documentation states this limit includes thought + visible output.
        // Count both in settlement; never change the service thinking settings.
        long thoughtBound=0;
        long input;
        final String countedProvider=provider,countedModel=model;
        try {input=role.equals("TARGET")
                ? com.example.projectmm.application.dialogue.DialogueTiming.stage("TARGET_TOKEN_COUNT",()->counter.count(countedProvider,countedModel,modified,embedding))
                : counter.count(provider,model,modified,embedding);
            if(input<0)throw new IllegalStateException();}
        catch(RuntimeException e){block("입력 토큰 계수 실패("+e.getClass().getSimpleName()+"): 최대 비용을 확인할 수 없어 전송하지 않았습니다.");return null;}
        long maxOutput=Math.addExact(output,thoughtBound);
        boolean large=rate.largeContextThreshold()>0&&input>rate.largeContextThreshold();
        BigDecimal im=large?rate.largeInputMultiplier().max(BigDecimal.ONE):BigDecimal.ONE;
        BigDecimal om=large?rate.largeOutputMultiplier().max(BigDecimal.ONE):BigDecimal.ONE;
        BigDecimal worstInput=rate.input().max(rate.cached()).max(rate.written());
        var reservation=worstInput.multiply(BigDecimal.valueOf(input)).multiply(im)
                .add(rate.output().multiply(BigDecimal.valueOf(maxOutput)).multiply(om)).movePointLeft(6);
        long expected=role.equals("TARGET")?policy.path("expectedTargetOutputTokens").asLong(1000):role.equals("PERSONA")?500:500;
        final String selectedPurpose=purpose;
        var recent=calls.values().stream().filter(c->c.usd()!=null&&c.model().equals(model)&&c.role().equals(role)&&c.purpose().equals(selectedPurpose)&&c.output()>0)
                .map(Receipt::output).toList();
        var tail=recent.subList(Math.max(0,recent.size()-12),recent.size());
        if(!tail.isEmpty())expected=Math.round(tail.stream().mapToLong(Long::longValue).average().orElse(expected));
        long safe=Math.max(expected*2,tail.stream().mapToLong(Long::longValue).max().orElse(expected)+512);
        expected=Math.min(expected,maxOutput);safe=Math.min(safe,maxOutput);
        BigDecimal estimate=rate.input().multiply(BigDecimal.valueOf(input)).multiply(im).add(rate.output().multiply(BigDecimal.valueOf(expected)).multiply(om)).movePointLeft(6);
        BigDecimal safeEstimate=worstInput.multiply(BigDecimal.valueOf(input)).multiply(im).add(rate.output().multiply(BigDecimal.valueOf(safe)).multiply(om)).movePointLeft(6);
        budget.put("inputTokens",input);budget.put("outputCapTokens",output);budget.put("additionalThoughtBoundTokens",thoughtBound);
        budget.put("expectedOutputTokens",expected);budget.put("safeExpectedOutputTokens",safe);budget.put("expectedUsd",estimate);
        budget.put("safeExpectedUsd",safeEstimate);budget.put("safetyMarginUsd",safeEstimate.subtract(estimate));
        budget.put("nextMaximumUsd",reservation);budget.put("largeContextSurcharge",large);
        if(spent.add(reserved).add(reservation).compareTo(spec.budgetUsd())>0)
            block("잔액보다 다음 호출의 최대 예약액이 커서 API 전송 전에 중단했습니다. 예산·모델·응답 상한을 자동 변경하지 않습니다.");
        body=mapper.writeValueAsBytes(modified);request.getHeaders().setContentLength(body.length);
        budget.put("requestHash",java.util.HexFormat.of().formatHex(digest(body)));
        reserved=reserved.add(reservation);budgetEvent("RESERVED",null);
        String id=UUID.randomUUID().toString();
        Files.write(directory.resolve("requests").resolve(id+".json"),body,StandardOpenOption.CREATE_NEW);
        budget.put("callId",id);
        record(new Receipt(id,Instant.now().toString(),"REQUEST_STARTED",role,provider,model,purpose,0,0,null,0,"UNKNOWN",null,null));
        try {
            if(role.equals("TARGET"))com.example.projectmm.application.dialogue.DialogueTiming.targetDispatching();
            var response=execution.execute(request,body);
            boolean streamed=modified.path("stream").asBoolean(false)&&response.getStatusCode().is2xxSuccessful()
                    &&response.getHeaders().getContentType()!=null
                    &&response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM);
            byte[] bytes;
            try {
                bytes=streamed?com.example.projectmm.infrastructure.dialogue.OpenAiResponseStream.read(response.getBody(),
                        com.example.projectmm.application.dialogue.DialogueTokens::publish).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        :response.getBody().readAllBytes();
            } catch(IOException|RuntimeException failure) {response.close();throw failure;}
            var root=mapper.readTree(bytes);
            // Keep the review result, not HTTP/auth headers, for offline rejection diagnosis.
            // A receipt failure must not hide already-incurred provider usage.
            if("SCENE_CONTINUITY".equals(purpose)) {
                try {
                    var reviews=directory.resolve("review-responses");Files.createDirectories(reviews);
                    var texts=new ArrayList<String>();
                    for(var outputItem:root.path("output"))for(var part:outputItem.path("content"))
                        if("output_text".equals(part.path("type").asText()))texts.add(part.path("text").asText());
                    Files.writeString(reviews.resolve(id+".json"),mapper.writeValueAsString(Map.of(
                            "callId",id,"status",root.path("status").asText("UNKNOWN"),"outputTexts",texts)),StandardOpenOption.CREATE_NEW);
                } catch(IOException receiptFailure) {
                    System.err.println("QA review receipt unavailable: "+receiptFailure.getClass().getName());
                }
            }
            var u=root.path(gemini?"usageMetadata":"usage");var d=u.path("input_tokens_details");
            long in=u.path(gemini?"promptTokenCount":embedding?"prompt_tokens":"input_tokens").asLong(-1),cache=embedding?0:(gemini?u.path("cachedContentTokenCount"):d.path("cached_tokens")).asLong(gemini?0:-1);
            Long write=gemini||embedding?Long.valueOf(0):d.path("cache_write_tokens").isIntegralNumber()?Long.valueOf(d.path("cache_write_tokens").asLong()):null;
            long out=embedding?0:gemini?u.path("candidatesTokenCount").asLong(0)+u.path("thoughtsTokenCount").asLong(0):u.path("output_tokens").asLong(-1);
            BigDecimal cost=rate.cost(in,cache,write,out);
            if(cost==null)uncertain=true;else spent=spent.add(cost);
            boolean violated=in>input||out>maxOutput||(cost!=null&&cost.compareTo(reservation)>0);
            if(violated)uncertain=true;
            String status=embedding?"EMBEDDING_RECEIVED":gemini?root.path("candidates").path(0).path("finishReason").asText("UNKNOWN"):root.path("status").asText("UNKNOWN");
            if(!response.getStatusCode().is2xxSuccessful())status="HTTP_"+response.getStatusCode().value();
            record(new Receipt(id,Instant.now().toString(),"RESPONSE_RECEIVED",role,provider,model,purpose,in,cache,write,out,status,cost,null));
            if(!uncertain)reserved=reserved.subtract(reservation);
            budget.put("lastActualUsd",cost);budget.put("differenceFromExpectedUsd",cost==null?null:cost.subtract(estimate));
            budgetEvent(uncertain?"REVIEW_REQUIRED":"SETTLED",violated?"공급자 사용량이 검증한 과금 경계를 벗어났습니다. 추가 호출 차단.":cost==null?"과금 사용량 미확인: 예약을 유지하고 추가 호출을 차단합니다.":null);
            byte[] safeBytes=response.getStatusCode().is2xxSuccessful()?bytes:"{\"error\":{\"message\":\"Provider rejected request; inspect safe ledger metadata\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new ClientHttpResponse() {
                public HttpStatusCode getStatusCode() throws IOException{return response.getStatusCode();}
                public String getStatusText() throws IOException{return response.getStatusText();}
                public HttpHeaders getHeaders(){
                    if(!streamed)return response.getHeaders();
                    var headers=new HttpHeaders();headers.putAll(response.getHeaders());
                    headers.setContentType(MediaType.APPLICATION_JSON);headers.setContentLength(safeBytes.length);return headers;
                }
                public InputStream getBody(){return new ByteArrayInputStream(safeBytes);}
                public void close(){response.close();}
            };
        } catch(IOException|RuntimeException e) {
            uncertain=true;
            var received=calls.get(id);
            boolean known=received!=null&&received.usd()!=null;
            try {budgetEvent("REVIEW_REQUIRED",known?"응답 사용량은 기록됐지만 로컬 정산 저장에 실패했습니다. 추가 유료 호출 차단.":"통신 또는 응답 처리 오류: 예약 유지, 자동 재시도 금지.");}
            catch(IOException saveError){e.addSuppressed(saveError);}
            // A local write failure cannot erase a durably recorded provider receipt.
            try {
                record(known?new Receipt(id,Instant.now().toString(),"LOCAL_PERSISTENCE_FAILED",role,provider,model,purpose,
                        received.input(),received.cached(),received.written(),received.output(),received.status(),received.usd(),e.getClass().getName())
                        :new Receipt(id,Instant.now().toString(),"FAILED",role,provider,model,purpose,0,0,null,0,"UNKNOWN",null,e.getClass().getName()));
            } catch(IOException saveError){e.addSuppressed(saveError);}
            if(e instanceof IOException)throw new IOException("Experience request failed: "+e.getClass().getName());
            throw new IllegalStateException("Experience request failed: "+e.getClass().getName());
        }
    }
}
