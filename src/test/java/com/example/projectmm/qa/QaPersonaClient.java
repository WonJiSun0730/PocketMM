package com.example.projectmm.qa;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/** Small persona-only Responses request. No target prompt, oracle or whole transcript. */
public final class QaPersonaClient implements QaRunner.Persona {
    static final String PROMPT_VERSION="qa-persona-v1";
    private final QaSpec spec;
    private final String key;
    private final RestClient client;
    private final ObjectMapper mapper=new ObjectMapper();
    public QaPersonaClient(QaSpec spec,String key) {
        this(spec,key,RestClient.builder().baseUrl("https://api.openai.com")
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()) {{setReadTimeout(Duration.ofSeconds(60));}})
                .build());
    }
    QaPersonaClient(QaSpec spec,String key,RestClient client) {this.spec=spec;this.key=key;this.client=client;}
    Map<String,Object> request(QaRunner.PersonaInput input) {
        var p=input.persona();
        String mode=switch(p.kind()) {
            case EVERYDAY -> "일상과 가벼운 관계 형성을 중심으로 반응한다.";
            case EMOTIONAL -> "작은 감정 변화, 서운함, 화해를 자연스럽게 표현한다.";
            case EVENT -> "작은 선물, 외출, 약속 등 새 사건의 여지를 만든다.";
            case CORRECTION -> "정정된 이야기에 반응하되 기존 중요한 사실을 임의로 바꾸지 않는다.";
            case MIXED -> "일상, 감정, 작은 사건을 대화 흐름에 따라 섞는다.";
        };
        String instructions="""
                당신은 가상 세계의 일반 사용자로 롤플레잉한다. 상대의 직전 답변을 읽고 자연스럽게 반응한다.
                질문만 반복하거나 매번 기억을 시험하지 않는다. 상대 캐릭터의 행동과 속마음을 대신 결정하지 않는다.
                때로 일상, 감정, 작은 사건, 화제 전환, 가벼운 시간 경과를 섞는다.
                사용자 자신의 발화/행동 하나만 짧은 한국어로 출력한다. 행동 묘사는 *별표*로 감싼다.
                대화 밖의 평가, 시스템, 정답표나 테스트 실행에 관해 말하지 않는다.
                """+mode+"\n유형: "+p.kind()+"\n성격: "+p.personality()+"\n말투: "+p.speech()+"\n관계: "+p.relationship()
                +"\n행동 경향: "+p.tendencies()+"\n목적: "+p.goal();
        var messages=new ArrayList<Map<String,String>>();
        for(var e:input.recent()) {
            messages.add(Map.of("role","assistant","content",clip(e.user(),4000)));
            messages.add(Map.of("role","user","content",clip(e.reply(),6000)));
        }
        messages.add(Map.of("role","user","content","이 흐름에 이어질 나의 다음 발화나 행동 하나를 써 줘."));
        int max=Integer.getInteger("pocket.qa.persona-max-output",512);
        if(max<16||max>2048)throw new IllegalArgumentException("Persona output limit must be 16..2048");
        var request=new LinkedHashMap<String,Object>();
        request.put("model",spec.personaModel());request.put("store",false);request.put("instructions",instructions);
        request.put("input",messages);request.put("max_output_tokens",max);
        String effort=System.getProperty("pocket.qa.persona-reasoning");
        if(effort!=null)request.put("reasoning",Map.of("effort",effort));
        if(mapper.writeValueAsString(request).length()>70000)throw new IllegalArgumentException("Persona request too large");
        return request;
    }
    @Override public QaRunner.Generation next(QaRunner.PersonaInput input) {
        var calls=new ArrayList<QaRunner.Usage>();
        var diagnostics=new ArrayList<QaRunner.Diagnostic>();
        String phase="REQUEST_PREPARATION";
        try {
            var payload=request(input);
            phase="REQUEST_STARTED";diagnostics.add(QaRunner.diagnostic(phase,null));
            String response=client.post().uri("/v1/responses").header("Authorization","Bearer "+key)
                    .body(payload).retrieve().body(String.class);
            phase="RESPONSE_RECEIVED";diagnostics.add(QaRunner.diagnostic(phase,null));
            var root=mapper.readTree(response);var u=root.path("usage");
            if(u.isObject()) {
                var d=u.path("input_tokens_details");Long write=d.path("cache_write_tokens").isIntegralNumber()?d.path("cache_write_tokens").asLong():null;
                long in=u.path("input_tokens").asLong(-1),out=u.path("output_tokens").asLong(-1),cached=d.path("cached_tokens").asLong(-1);
                var rate=spec.prices().get(spec.personaModel());
                var usd=rate==null?null:rate.cost(in,cached,write,out);
                calls.add(new QaRunner.Usage("PERSONA",spec.personaModel(),PROMPT_VERSION,in,cached,write,out,"USAGE_REPORTED",usd,
                        root.path("id").asText(UUID.randomUUID().toString()),0));
            } else calls.add(unknown("USAGE_NOT_REPORTED"));
            phase="USAGE_PARSED";diagnostics.add(QaRunner.diagnostic(phase,null));
            if(!"completed".equals(root.path("status").asText()))return new QaRunner.Generation("",calls,"Persona response incomplete",diagnostics);
            var text=new StringBuilder();
            for(var item:root.path("output"))if("message".equals(item.path("type").asText()))
                for(var part:item.path("content"))if("output_text".equals(part.path("type").asText()))text.append(part.path("text").asText());
            return new QaRunner.Generation(text.toString(),calls,null,diagnostics);
        } catch(RuntimeException ex) {
            diagnostics.add(QaRunner.diagnostic(phase,ex));
            if(calls.isEmpty())calls.add(unknown(ex instanceof RestClientResponseException h?"HTTP_"+h.getStatusCode().value():"REQUEST_OR_PARSE_FAILED"));
            return new QaRunner.Generation("",calls,"Persona call failed; no automatic retry",diagnostics);
        }
    }
    private QaRunner.Usage unknown(String status) {return new QaRunner.Usage("PERSONA",spec.personaModel(),PROMPT_VERSION,0,0,null,0,status,null);}
    private static String clip(String value,int max){return value.length()<=max?value:value.substring(0,max);}
}
