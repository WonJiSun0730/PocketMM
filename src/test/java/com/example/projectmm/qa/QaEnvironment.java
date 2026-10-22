package com.example.projectmm.qa;

import com.example.projectmm.application.chat.*;
import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.application.memory.*;
import com.example.projectmm.application.time.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterProfile;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.member.Member;
import com.example.projectmm.domain.memory.ChatMemoryCandidate;
import com.example.projectmm.domain.world.World;
import com.example.projectmm.infrastructure.dialogue.*;
import com.example.projectmm.infrastructure.memory.OpenAiChatMemoryExtractor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@TestConfiguration(proxyBeanMethods=false)
@Import({WorldRoomDialogueService.class,WorldRoomSessionService.class,WorldRoomContextBuilder.class,
        PocketMemoryCommitListener.class,PocketTurnTestSnapshotService.class,PocketMemoryInspectorService.class,
        PocketMemoryService.class,PocketMemoryWriter.class,MemoryDetailService.class,MemoryRecallService.class,
        MemoryDecayPolicy.class,MemoryTierPolicy.class,MemoryLifecycleService.class,MemoryCompressionService.class,
        MemoryCompressionPersistenceService.class,MemoryPatternCandidateFinder.class,MemoryProvenanceAccessPolicy.class,
        ProtectedMemoryDetailPolicy.class})
public class QaEnvironment {
    @Bean ObjectMapper qaMapper() { return new ObjectMapper(); }
    @Bean AiUsageTracker qaUsage() { return new AiUsageTracker(); }
    @Bean QaSpendGuard qaSpendGuard(QaSpec spec) {return new QaSpendGuard(spec);}
    @Bean TransactionTemplate qaTransactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    @Bean TimeInterpreter qaTime() { return new RuleBasedTimeInterpreter(); }
    @Bean MemoryRetriever qaRetriever(QaSpec spec) {
        return spec.memoryEnabled()?new MemoryRetrievalRanker():(q,c,l)->List.of();
    }
    @Bean MemoryCompressor qaCompressor() { return (memory,level,related)->null; }
    @Bean QaSpec qaSpec() throws java.io.IOException {
        if(!Boolean.getBoolean("pocket.qa.execute"))return QaSpec.mock("integration",100);
        String path=System.getProperty("pocket.qa.config");
        if(path==null)throw new IllegalArgumentException("Explicit pocket.qa.config path required");
        return new ObjectMapper().readValue(Files.readString(Path.of(path)),QaSpec.class);
    }
    @Bean ChatMemoryExtractor qaExtractor(QaSpec spec,ObjectMapper mapper,AiUsageTracker usage,QaSpendGuard guard) {
        if(!spec.memoryEnabled())return text->List.of();
        if(spec.paid()) {
            var extractor=new OpenAiChatMemoryExtractor(key(),spec.extractionModel(),mapper);
            ReflectionTestUtils.setField(extractor,"usageTracker",usage);
            ReflectionTestUtils.setField(extractor,"client",qaClient(guard));
            return extractor;
        }
        return text->{
            usage.record(AiUsageTracker.currentSessionId(),"MOCK",spec.extractionModel(),0,0,0L,0,0);
            if(text.contains("각인이 뭐")||text.contains("어디였지")||text.contains("알려줘"))return List.of();
            return text.contains("나침반")||text.contains("약속") ? List.of(new ChatMemoryCandidate(text,8)):List.of();
        };
    }
    @Bean DialogueGateway qaGateway(QaSpec spec,ObjectMapper mapper,AiUsageTracker usage,QaSpendGuard guard) {
        if(spec.paid()) {
            var builder=new GeminiDialogueGateway("","unused",mapper);
            var target=new OpenAiDialogueGateway(key(),spec.targetModel(),mapper,builder,usage);
            ReflectionTestUtils.setField(target,"restClient",qaClient(guard));
            return target;
        }
        return new DialogueGateway() {
            public String reviewWorldRoomContinuity(WorldRoomDialogueContext c, String evidence) {
                var request=mapper.readTree(evidence); var result=mapper.createObjectNode();
                result.put("approved",true);result.set("turn",request.path("candidate"));result.putArray("changes");result.putArray("conflicts");
                return mapper.writeValueAsString(result);
            }
            public String generate(DialogueContext context,String message) {throw new UnsupportedOperationException("QA only world-room");}
            public WorldRoomTurn generateWorldRoomTurn(WorldRoomDialogueContext c,String message) {
                usage.record(c.sessionId(),"MOCK",spec.targetModel(),0,0,0L,0,0);
                var memories=c.recalledMemories().stream().flatMap(m->m.memories().stream()).map(WorldRoomDialogueContext.RecalledMemory::summary).toList();
                String reply=memories.isEmpty()?"잘 기억나지 않지만, 지금 해 준 이야기는 알겠어. 오늘은 산책을 이어가자.":"떠오르는 이야기는 "+String.join(" / ",memories);
                return new WorldRoomTurn(reply,WorldRoomCastChange.none(),List.of(),60);
            }
        };
    }
    static QaRunner.Persona mockPersona() {
        String[] lines={"오늘 시장에서 재미있는 걸 봤어. 너는 하루가 어땠어?","네 말을 듣고 나니 조금 안심이 돼. 잠깐 같이 걸을래?",
                "비가 그친 길이 조용하네. 잠시 풍경을 보자.","조금 전 이야기에서 네가 조심스러워 보였어. 괜찮니?",
                "차 한 잔 마시면서 다른 얘기도 해 보고 싶어.","오늘은 혼자보다 같이 있으니 덜 긴장되네.",
                "시간이 조금 흘렀네. 골목 끝까지 천천히 걸어가자.","나는 방금 한 말을 네가 어떻게 받아들였는지도 궁금해."};
        return i->{
            String line=lines[(i.turn()-1)%lines.length];
            if(!i.recent().isEmpty()&&i.recent().getLast().reply().contains("산책"))line="산책 좋지. "+line;
            return new QaRunner.Generation(line,List.of(),null);
        };
    }
    static String key() {
        String key=System.getenv("POCKET_QA_OPENAI_API_KEY");
        if(key==null||key.isBlank())throw new IllegalArgumentException("Dedicated QA API key is required");
        return key;
    }
    static org.springframework.web.client.RestClient qaClient(QaSpendGuard guard) {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return org.springframework.web.client.RestClient.builder().baseUrl("https://api.openai.com").requestFactory(factory).requestInterceptor(guard).build();
    }
    public record Fixture(long sessionId,long characterId) {}
    static Fixture fixture(String runId,QaSpec spec,EntityManager em,TransactionTemplate tx,WorldRoomSessionService sessions) {
        return tx.execute(status->{
            var encoder=new PasswordEncoder() {
                public String encode(CharSequence raw){return "qa-disabled-login";}
                public boolean matches(CharSequence raw,String encoded){return false;}
            };
            var owner=Member.createLocal("qa-"+QaPipeline.hash(runId).substring(0,16),"qa-disabled-login","QA synthetic","qa-"+runId+"@example.invalid",encoder);em.persist(owner);
            var world=new World(owner,"QA 합성 세계","실제 이용자 정보 없는 개발 검증 공간");em.persist(world);
            var character=new Character("QA 동료",owner);character.assignWorld(world);em.persist(character);
            em.persist(new CharacterProfile(character,"차분하고 신중한 동료",null,null,"상대의 말을 수용하되 기억이 불확실하면 인정한다.",
                    "자연스러운 한국어","QA 합성 인물",null));
            var at=LocalDateTime.of(2026,1,1,12,0);
            var room=new WorldConversation(world,owner,"QA "+runId,WorldConversation.Visibility.PRIVATE,at);em.persist(room);
            var intro=new WorldRoomIntro(room,"QA 시작","동료와 마을 광장에서 이야기를 시작한다.",0,at,"마을 광장",List.of("편안함"),List.of("가방"));em.persist(intro);
            var session=sessions.start(room.getId(),owner.getLoginId(),null,intro.getId());
            session.updatePocketSettings(null,null,true,"OPENAI",spec.targetModel());
            em.createQuery("select c from WorldRoomSessionCast c where c.session.id=:id",WorldRoomSessionCast.class).setParameter("id",session.getId())
                    .getResultList().forEach(c->c.enter(at));
            return new Fixture(session.getId(),character.getId());
        });
    }
    static QaPipeline pipeline(QaSpec spec,long id,WorldRoomDialogueService dialogue,WorldRoomMessageRepository messages,
                               WorldRoomMessageVariantRepository variants,PocketMemoryInspectorService inspector,AiUsageTracker usage,
                               JdbcTemplate jdbc,TransactionTemplate tx) {
        return new QaPipeline(spec,id,dialogue,messages,variants,inspector,usage,jdbc,tx);
    }
}
