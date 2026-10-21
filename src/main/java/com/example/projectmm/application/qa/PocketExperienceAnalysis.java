package com.example.projectmm.application.qa;
import tools.jackson.databind.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Local evidence only. Semantic character/memory correctness remains explicitly unreviewed. */
public final class PocketExperienceAnalysis {
    private static final ObjectMapper M=new ObjectMapper();
    public record Distribution(long count,double min,double median,double max,double average,Map<String,Long> buckets) {}
    public record Finding(int turn,String type,String evidence,String assessment) {}
    public record Detail(int turn,long inputTokens,long outputTokens,long cachedTokens,int replyCharacters,int dialogueCharacters,
                         int narrationCharacters,long latencyMillis,boolean usageKnown,String memoryEvidence,String semanticReview) {}
    public record Result(BigDecimal serviceUsd,BigDecimal operatingUsd,BigDecimal playerUsd,int unknownCalls,
                         Distribution replyCharacters,Distribution latencyMillis,Distribution outputTokens,
                         double cacheHitRate,List<Detail> turns,List<Finding> findings,String verdict) {}
    static Distribution distribution(List<Long> values) {
        var sorted=values.stream().sorted().toList();var buckets=new LinkedHashMap<String,Long>();
        buckets.put("0–39",values.stream().filter(v->v<40).count());buckets.put("40–199",values.stream().filter(v->v>=40&&v<200).count());
        buckets.put("200–599",values.stream().filter(v->v>=200&&v<600).count());buckets.put("600–1499",values.stream().filter(v->v>=600&&v<1500).count());buckets.put("1500+",values.stream().filter(v->v>=1500).count());
        int n=sorted.size();return new Distribution(n,n==0?0:sorted.getFirst(),n==0?0:n%2==0?(sorted.get(n/2-1)+sorted.get(n/2))/2.0:sorted.get(n/2),n==0?0:sorted.getLast(),values.stream().mapToLong(Long::longValue).average().orElse(0),buckets);
    }
    public static Result analyze(JsonNode state) {
        BigDecimal service=BigDecimal.ZERO,player=BigDecimal.ZERO;int unknown=0;long allIn=0,cache=0;
        var calls=new HashMap<Integer,List<JsonNode>>();
        for(var c:state.path("calls")) {
            calls.computeIfAbsent(c.path("turn").asInt(),i->new ArrayList<>()).add(c);
            if(c.path("usd").isNull()||c.path("usd").isMissingNode())unknown++;
            else if(c.path("role").asText().equals("PERSONA"))player=player.add(c.path("usd").decimalValue());else service=service.add(c.path("usd").decimalValue());
            if(c.path("role").asText().equals("TARGET")){allIn+=c.path("input").asLong();cache+=c.path("cached").asLong();}
        }
        var details=new ArrayList<Detail>();var findings=new ArrayList<Finding>();var lengths=new ArrayList<Long>();var times=new ArrayList<Long>();var outputs=new ArrayList<Long>();var seen=new HashMap<String,Integer>();
        for(var call:state.path("calls"))if(!Set.of("completed","STOP","USAGE_REPORTED","BUDGET_BLOCKED","EMBEDDING_RECEIVED").contains(call.path("status").asText()))
            findings.add(new Finding(call.path("turn").asInt(),"CALL_STATUS",call.path("role").asText()+" / "+call.path("status").asText(),"REVIEW_REQUIRED"));
        var quote=Pattern.compile("[\"“「『](.*?)[\"”」』]",Pattern.DOTALL);
        for(var t:state.path("turns")) {
            int number=t.path("number").asInt();String reply=t.path("outcome").path("reply").asText();int characters=reply.codePointCount(0,reply.length()),dialogue=0;
            var matcher=quote.matcher(reply);while(matcher.find())dialogue+=matcher.group(1).codePointCount(0,matcher.group(1).length());
            long in=0,out=0,cached=0;boolean known=true;int targetCalls=0;
            for(var c:calls.getOrDefault(number,List.of()))if(c.path("role").asText().equals("TARGET")) {
                targetCalls++;in+=c.path("input").asLong();out+=c.path("output").asLong();cached+=c.path("cached").asLong();known&=!c.path("usd").isNull();
                if(!Set.of("completed","STOP","USAGE_REPORTED").contains(c.path("status").asText()))findings.add(new Finding(number,"RESPONSE_STATUS",c.path("status").asText(),"REVIEW_REQUIRED"));
            }
            String normalized=reply.replaceAll("\\s+"," ").trim();Integer previous=seen.putIfAbsent(normalized,number);
            if(previous!=null&&!normalized.isEmpty())findings.add(new Finding(number,"EXACT_REPEAT","응답 전체가 "+previous+"턴과 동일","REVIEW_CANDIDATE"));
            if(characters<40)findings.add(new Finding(number,"SHORT_RESPONSE","본문 "+characters+"자. 상황에 적절하면 정상","REVIEW_CANDIDATE"));
            var fragments=reply.split("[.!?。！？\\n]");for(String fragment:fragments)if(fragment.strip().length()>=20) {
                String key="phrase:"+fragment.strip();Integer old=seen.putIfAbsent(key,number);
                if(old!=null&&old!=number)findings.add(new Finding(number,"REPEATED_EXPRESSION",old+"턴과 반복: "+fragment.strip().substring(0,Math.min(100,fragment.strip().length())),"REVIEW_CANDIDATE"));
            }
            String evidence="검토 필요";
            try {
                var context=M.readTree(t.path("outcome").path("evaluationJson").asText("{}")).path("context");
                evidence="주입된 기억: "+M.writeValueAsString(context.path("recalledMemories"))+"; 문장에 실제 반영됐는지는 의미 검토 필요";
            } catch(RuntimeException ignored) {evidence="기억 주입 증거 없음 · 확인 필요";}
            var actions=Pattern.compile("\\*([^*\\n]{4,120})\\*").matcher(reply);
            while(actions.find()) {
                String action=actions.group(1).strip();String key="action:"+action;Integer old=seen.putIfAbsent(key,number);
                if(old!=null&&old!=number)findings.add(new Finding(number,"REPEATED_ACTION",old+"턴과 같은 행동 묘사: "+action,"REVIEW_CANDIDATE"));
            }
            if(reply.strip().matches("(?s).*[,:：，—-]$"))findings.add(new Finding(number,"POSSIBLE_TRUNCATION","문장이 연결 기호로 끝남. 원본 응답 상태와 함께 검토","REVIEW_CANDIDATE"));
            long elapsed=t.path("latencyMillis").asLong();lengths.add((long)characters);times.add(elapsed);if(known&&targetCalls>0)outputs.add(out);
            details.add(new Detail(number,in,out,cached,characters,dialogue,Math.max(0,characters-dialogue),elapsed,known&&targetCalls>0,evidence,"NOT_REVIEWED"));
        }
        if(lengths.size()>=10&&lengths.stream().filter(v->v<200).count()>lengths.size()*0.7)findings.add(new Finding(0,"SHORT_BIAS","200자 미만 응답이 70% 초과. 문체·상황·입력 분포를 함께 검토","REPRESENTATIVENESS_REVIEW"));
        return new Result(service,service.add(player),player,unknown,distribution(lengths),distribution(times),distribution(outputs),allIn==0?0:(double)cache/allIn,details,findings,"실행 완료 여부와 서사 품질 합격은 별개 · 캐릭터/설정/기억 의미 검토 미완료");
    }
    private static BigDecimal decimal(JsonNode node){return node.isNumber()?node.decimalValue():BigDecimal.ZERO;}
    public static JsonNode fullAnalysis(JsonNode state,JsonNode manifest) {return fullAnalysis(state,manifest,List.of());}
    public static JsonNode fullAnalysis(JsonNode state,JsonNode manifest,List<JsonNode> ledger) {
        var result=analyze(state);var node=(tools.jackson.databind.node.ObjectNode)M.valueToTree(result);
        int completed=state.path("turns").size();var estimates=manifest.path("estimatedPerTurn");
        BigDecimal low=decimal(estimates.path("lowUsd")).multiply(BigDecimal.valueOf(completed));
        BigDecimal high=decimal(estimates.path("highUsd")).multiply(BigDecimal.valueOf(completed));
        long in=result.turns().stream().mapToLong(Detail::inputTokens).sum(),out=result.turns().stream().mapToLong(Detail::outputTokens).sum();
        var comparison=new LinkedHashMap<String,Object>();comparison.put("expectedOperatingLowToDate",low);comparison.put("expectedOperatingHighToDate",high);
        comparison.put("expectedServiceLowToDate",decimal(estimates.path("serviceLowUsd")).multiply(BigDecimal.valueOf(completed)));
        comparison.put("expectedServiceHighToDate",decimal(estimates.path("serviceHighUsd")).multiply(BigDecimal.valueOf(completed)));
        comparison.put("differenceFromHigh",result.operatingUsd().subtract(high));
        comparison.put("initialEstimatedInputTokens",manifest.path("estimateBasis").path("estimatedInputTokens").asLong());comparison.put("initialObservedOutputTokens",manifest.path("estimateBasis").path("observedOutputTokens").asLong());
        comparison.put("actualMeanTargetInputTokens",completed==0?0:(double)in/completed);comparison.put("actualMeanTargetOutputTokens",completed==0?0:(double)out/completed);
        comparison.put("targetInputTokens",in);comparison.put("targetOutputTokens",out);comparison.put("targetCacheHitRate",result.cacheHitRate());
        comparison.put("callCount",ledger.isEmpty()?state.path("calls").size():ledger.stream().map(c->c.path("callId").asText()).distinct().count());comparison.put("estimateAtStart",estimates);
        comparison.put("explanation","예상은 캐시 미반영 범위입니다. 차이는 실제 입력·출력 토큰, 캐시 적중률, 보조 호출 횟수를 함께 비교해야 합니다. 출력이 짧다는 사실만으로 실패나 원인을 확정하지 않습니다.");
        node.set("comparison",M.valueToTree(comparison));node.set("budgetPolicy",manifest.path("budgetPolicy"));node.set("intentionalDifferences",manifest.path("differences"));
        long priorOutput=manifest.path("estimateBasis").path("observedOutputTokens").asLong();
        if(completed>=10&&priorOutput>0&&(double)out/completed<priorOutput*0.5)
            ((tools.jackson.databind.node.ArrayNode)node.path("findings")).add(M.valueToTree(new Finding(0,"RELATIVE_SHORT_BIAS","기존 응답 기준 "+priorOutput+" 출력 토큰 대비 실측 평균 "+(out/completed)+"토큰. 모델·입력 행동·기억 주입과 문체를 함께 검토","REPRESENTATIVENESS_REVIEW")));
        var durations=new HashMap<String,Long>();var started=new HashMap<String,java.time.Instant>();
        // Both STARTED and RECEIVED records are retained in the full ledger, not only the last state.
        for(var receipt:ledger) {
            String id=receipt.path("callId").asText();
            if(receipt.path("at").asText().isBlank())continue;
            if(receipt.path("phase").asText().equals("REQUEST_STARTED"))started.put(id,java.time.Instant.parse(receipt.path("at").asText()));
            else if(started.containsKey(id))durations.put(id,java.time.Duration.between(started.get(id),java.time.Instant.parse(receipt.path("at").asText())).toMillis());
        }
        var generation=new ArrayList<Long>();
        for(var detail:node.path("turns")) {
            long millis=0;boolean measured=false;
            for(var call:state.path("calls"))if(call.path("turn").asInt()==detail.path("turn").asInt()&&call.path("role").asText().equals("TARGET")&&durations.containsKey(call.path("callId").asText())) {millis+=durations.get(call.path("callId").asText());measured=true;}
            ((tools.jackson.databind.node.ObjectNode)detail).put("generationMillis",measured?millis:-1);
            if(measured)generation.add(millis);
        }
        node.set("generationMillis",M.valueToTree(distribution(generation)));return node;
    }
    public static String markdown(JsonNode state,PocketExperienceSnapshot snapshot) {
        var a=analyze(state);return "# 실 비용 및 체감 테스트\n\n상태: "+state.path("status").asText()+"\n\n"+a.verdict()+"\n\n실서비스 확인 비용: $"+a.serviceUsd()+"\n\n테스트 운영 확인 비용: $"+a.operatingUsd()+"\n\n미확인 호출: "+a.unknownCalls()+"\n\n## 분석 원본\n\n```json\n"+M.writerWithDefaultPrettyPrinter().writeValueAsString(a)+"\n```\n\n## 의도적 차이\n\n"+String.join("\n\n",snapshot.differences())+"\n\n대사 분량은 인용부호 내부, 나레이션은 나머지 본문의 휴리스틱이며 오분류 가능성이 있습니다. 응답 생성 시간은 실제 저장·기억 후처리까지 포함한 왕복 시간입니다. 짧음이나 기억 주입 자체로 품질 실패·성공을 확정하지 않습니다.\n";
    }
}
