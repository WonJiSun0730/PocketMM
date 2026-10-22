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
public class ExperienceEnvironment {
    @Bean ObjectMapper qaMapper() { return new ObjectMapper(); }
    @Bean AiUsageTracker qaUsage() { return new AiUsageTracker(); }
    @Bean ExperienceTransport qaSpendGuard(QaSpec spec) throws java.io.IOException {return new ExperienceTransport(spec,ExperienceExecutionTest.directory());}
    @Bean TransactionTemplate qaTransactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    @Bean com.example.projectmm.infrastructure.ai.TextProviderRouting qaTextRouting(QaSpec spec,ObjectMapper mapper,AiUsageTracker usage,ExperienceTransport guard) throws java.io.IOException {
        var snapshot=ExperienceExecutionTest.snapshot();
        if (spec.paid() && (!"OPENAI".equals(snapshot.extractorProvider()) ||
                java.util.stream.Stream.of(snapshot.extractorModel(),snapshot.timeModel(),snapshot.compressorModel(),snapshot.embeddingModel())
                    .filter(java.util.Objects::nonNull).anyMatch(m->m.startsWith("gemini"))))
            throw new IllegalStateException("이전 Gemini 보조 호출 설정은 차단되었습니다. OpenAI 설정으로 새 QA 실행을 만드세요.");
        var env=new org.springframework.core.env.StandardEnvironment();
        var props=new java.util.HashMap<String,Object>();
        props.put("openai.api-key",spec.paid()?key():"");
        if(snapshot.timeModel()!=null)props.put("openai.time-model",snapshot.timeModel());
        if(snapshot.compressorModel()!=null)props.put("openai.compression-model",snapshot.compressorModel());
        if(snapshot.embeddingModel()!=null)props.put("openai.embedding-model",snapshot.embeddingModel());
        env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("qa-openai",props));
        var routing=new com.example.projectmm.infrastructure.ai.TextProviderRouting(null,env,mapper,usage) {
            @Override public boolean openAi() {return true;}
        };
        ReflectionTestUtils.setField(routing,"client",qaClient(guard));return routing;
    }
    @Bean TimeInterpreter qaTime(QaSpec spec,ObjectMapper mapper,ExperienceTransport guard,com.example.projectmm.infrastructure.ai.TextProviderRouting routing) throws java.io.IOException {
        var snapshot=ExperienceExecutionTest.snapshot();
        com.example.projectmm.application.time.AiTimeParser parser=new com.example.projectmm.infrastructure.time.NoOpAiTimeParser();
        if(spec.paid()&&snapshot.timeModel()!=null) {
            var gemini=new com.example.projectmm.infrastructure.time.GeminiTimeParser("",snapshot.timeModel(),mapper);
            ReflectionTestUtils.setField(gemini,"textRouting",routing);parser=(input,time)->AiUsageTracker.withScope(AiUsageTracker.currentSessionId(),"TIME_INTERPRETATION",()->gemini.parse(input,time));
        }
        return new HybridTimeInterpreter(new RuleBasedTimeInterpreter(),new AiTimeInterpreter(parser,new TimeChangeResolver()));
    }
    @Bean MemoryRetriever qaRetriever(QaSpec spec,ObjectMapper mapper,ExperienceTransport guard,
            com.example.projectmm.infrastructure.memory.MemoryEmbeddingJpaRepository repository,com.example.projectmm.infrastructure.ai.TextProviderRouting routing) throws java.io.IOException {
        MemoryEmbeddingProvider provider=new UnavailableMemoryEmbeddingProvider();var snapshot=ExperienceExecutionTest.snapshot();
        if(spec.paid()&&snapshot.embeddingModel()!=null) {
            var gemini=new com.example.projectmm.infrastructure.ai.GeminiMemoryEmbeddingProvider("",snapshot.embeddingModel(),mapper);
            ReflectionTestUtils.setField(gemini,"textRouting",routing);
            provider=gemini;
        }
        var store=new com.example.projectmm.infrastructure.memory.JpaMemoryEmbeddingStore(repository,mapper);
        return new HybridMemoryRetriever(provider,new CachedMemoryEmbeddingService(provider,store),new MemoryRetrievalRanker());
    }
    @Bean com.example.projectmm.infrastructure.memory.MemoryCompressionPromptBuilder compressionPrompts(
            com.example.projectmm.domain.memory.MemoryAccessStateRepository access,
            MemoryProvenanceAccessPolicy provenance,ProtectedMemoryDetailPolicy detail) {
        return new com.example.projectmm.infrastructure.memory.MemoryCompressionPromptBuilder(access,provenance,detail);
    }
    @Bean MemoryCompressor qaCompressor(QaSpec spec, ObjectMapper mapper, ExperienceTransport guard,
            com.example.projectmm.infrastructure.memory.MemoryCompressionPromptBuilder prompts,com.example.projectmm.infrastructure.ai.TextProviderRouting routing) throws java.io.IOException {
        var snapshot=ExperienceExecutionTest.snapshot();
        if(!spec.paid()||snapshot.compressorModel()==null)return new RuleBasedMemoryCompressor();
        var compressor=new com.example.projectmm.infrastructure.memory.GeminiMemoryCompressor("",snapshot.compressorModel(),mapper,prompts);
        ReflectionTestUtils.setField(compressor,"textRouting",routing);return (memory,level,related)->AiUsageTracker.withScope(AiUsageTracker.currentSessionId(),"MEMORY_COMPRESSION",()->compressor.compress(memory,level,related));
    }
    @Bean QaSpec qaSpec() throws java.io.IOException {
        if(!Boolean.getBoolean("pocket.qa.execute"))return QaSpec.mock("integration",100);
        String path=System.getProperty("pocket.qa.config");
        if(path==null)throw new IllegalArgumentException("Explicit pocket.qa.config path required");
        return new ObjectMapper().readValue(Files.readString(Path.of(path)),QaSpec.class);
    }
    @Bean ChatMemoryExtractor qaExtractor(QaSpec spec,ObjectMapper mapper,AiUsageTracker usage,ExperienceTransport guard) {
        if(!spec.memoryEnabled())return text->List.of();
        if(spec.paid()) {
            try {
                var snapshot=ExperienceExecutionTest.snapshot();
                if(!snapshot.memoryEnabled()||snapshot.extractorProvider().equals("NONE"))return text->List.of();
                if(!snapshot.extractorProvider().equals("OPENAI"))throw new IllegalStateException("OpenAI QA only: auxiliary provider blocked");
                var extractor=new OpenAiChatMemoryExtractor(key(),snapshot.extractorModel(),mapper);
                ReflectionTestUtils.setField(extractor,"usageTracker",usage);ReflectionTestUtils.setField(extractor,"client",qaClient(guard));return extractor;
            } catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        }
        return text->{
            usage.record(AiUsageTracker.currentSessionId(),"MOCK",spec.extractionModel(),0,0,0L,0,0);
            if(text.contains("각인이 뭐")||text.contains("어디였지")||text.contains("알려줘"))return List.of();
            return text.contains("나침반")||text.contains("약속") ? List.of(new ChatMemoryCandidate(text,8)):List.of();
        };
    }
    @Bean DialogueGateway qaGateway(QaSpec spec,ObjectMapper mapper,AiUsageTracker usage,ExperienceTransport guard) {
        if(spec.paid()) {
            var builder=new GeminiDialogueGateway("","unused",mapper);
            var target=new OpenAiDialogueGateway(key(),spec.targetModel(),mapper,builder,usage);
            ReflectionTestUtils.setField(target,"restClient",qaClient(guard));
            return new PocketDialogueGatewayRouter(builder,target);
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
                String reply=c.world().worldName()+" · "+c.roomCast().stream().map(WorldRoomCharacterContext::characterName).toList()+" · "+(c.playerProfile()==null?"":c.playerProfile().name())+" · "+String.join(" / ",memories);
                return new WorldRoomTurn(reply,WorldRoomCastChange.none(),List.of(),60);
            }
        };
    }
    static String key() {
        String key=System.getenv("POCKET_QA_OPENAI_API_KEY");
        if(key==null||key.isBlank())throw new IllegalArgumentException("Dedicated QA API key is required");
        return key;
    }
    static org.springframework.web.client.RestClient qaClient(ExperienceTransport guard) {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return org.springframework.web.client.RestClient.builder().baseUrl("https://api.openai.com").requestFactory(factory).requestInterceptor(guard).build();
    }
    public record Fixture(long sessionId,long characterId) {}
    static QaPipeline pipeline(QaSpec spec,long id,WorldRoomDialogueService dialogue,WorldRoomMessageRepository messages,
                               WorldRoomMessageVariantRepository variants,PocketMemoryInspectorService inspector,AiUsageTracker usage,
                               JdbcTemplate jdbc,TransactionTemplate tx) {
        return new QaPipeline(spec,id,dialogue,messages,variants,inspector,usage,jdbc,tx);
    }
}
