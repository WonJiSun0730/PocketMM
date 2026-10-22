package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.application.memory.MemoryRecallService;
import com.example.projectmm.application.simulation.CatchUpSimulationService;
import com.example.projectmm.application.time.TimeInterpreter;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.player.*;
import com.example.projectmm.domain.member.*;
import com.example.projectmm.domain.world.*;
import com.example.projectmm.domain.memory.MemoryRepository;
import com.example.projectmm.domain.dialogue.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PocketProgressService.class, WorldRoomSessionService.class, WorldRoomDialogueService.class,
        WorldRoomContextBuilder.class, CatchUpSimulationService.class, WorldRoomService.class})
class PocketProgressIntegrationTest {
    @org.junit.jupiter.api.BeforeEach
    void approveContinuityForExistingScenario() {
        com.example.projectmm.application.dialogue.SceneContinuityTestSupport.approveUnchanged(gateway);
    }

    @Autowired PocketProgressService progress;
    @Autowired WorldRoomService roomService;
    @Autowired WorldRoomSessionService sessionService;
    @Autowired WorldRoomDialogueService dialogue;
    @Autowired WorldRoomSessionRepository sessions;
    @Autowired PocketActivityReportRepository reports;
    @Autowired WorldRoomMessageRepository messages;
    @Autowired WorldRoomPlayerStateRepository states;
    @Autowired WorldRoomMessageVariantRepository variants;
    @Autowired MemoryRepository memories;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired MemberRepository members;
    @Autowired WorldRepository worlds;
    @Autowired WorldConversationRepository rooms;
    @Autowired WorldRoomIntroRepository intros;
    @Autowired CharacterRepository characters;
    @Autowired PlayerProfileRepository players;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean DialogueGateway gateway;
    @MockitoBean MemoryRecallService recall;
    @MockitoBean TimeInterpreter timeInterpreter;
    @MockitoBean PresenceClock clock;
    static int sequence;
    String login; Long id, a, b; LocalDateTime now, story;
    <T> T db(java.util.function.Supplier<T> work) { return new TransactionTemplate(manager).execute(s -> work.get()); }
    @BeforeEach void setup() {
        int number = ++sequence; login = "absence" + number;
        now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        story = LocalDateTime.of(2026, 10, 8, 15, 0); when(clock.now()).thenReturn(now);
        db(() -> {
            var encoder = mock(PasswordEncoder.class); when(encoder.encode(anyString())).thenReturn("encoded");
            var owner = members.save(Member.createLocal(login, "password", "Tester", login+"@example.test", encoder));
            var world = worlds.save(new World(owner, "World", "A modern town"));
            var room = rooms.save(new WorldConversation(world, owner, "Home", WorldConversation.Visibility.PRIVATE, story));
            var intro = intros.save(new WorldRoomIntro(room, "Start", "At home", 0));
            var first = new Character("A"); first.assignWorld(world); first.updateModel("/models/a.vrm",1.7); a = characters.save(first).getId();
            var second = new Character("B"); second.assignWorld(world); second.updateModel("/models/b.vrm",1.8); b = characters.save(second).getId();
            var player = players.save(new PlayerProfile(number, "Player", "Student", null));
            player.updateModel("/models/player.vrm",1.6);
            var session = sessionService.start(room.getId(), login, player.getId(), intro.getId());
            id = session.getId(); session.updateSimulatedAt(story);
            states.findBySessionId(id).orElseThrow().update("Home", List.of("tired"), List.of("phone"));
            return null;
        });
        now = LocalDateTime.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        when(clock.now()).thenReturn(now);
    }
    @AfterEach void cleanup() { if (sessions.existsById(id)) sessionService.deletePocket(id, login); }
    @Test void deletingPocketCleansCompressedEvidenceAndKeepsIndependentMemory() {
        enabledAfter(Duration.ofMinutes(5)); progress.resume(id,login);
        var dependencyIds = seedMemoryDependencies();
        sessionService.deletePocket(id,login);
        assertThat(sessions.existsById(id)).isFalse();
        assertMemoryDependenciesRemoved(dependencyIds);
    }
    // The retained memories are outside the deleted Pocket, like shared character memories.
    long[] seedMemoryDependencies() {
        return db(() -> {
            var source = memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).getFirst();
            var actor = source.getCharacter();
            var dependent = memories.save(new com.example.projectmm.domain.memory.Memory(actor,null,"compressed",2,20,story));
            var transitive = memories.save(new com.example.projectmm.domain.memory.Memory(actor,null,"schema",2,20,story));
            var retained = memories.save(new com.example.projectmm.domain.memory.Memory(actor,null,"unrelated",2,20,story));
            em.persist(new com.example.projectmm.domain.memory.MemoryCompressionEvidence(dependent,
                    com.example.projectmm.domain.memory.MemoryDetailLevel.PATTERN,source));
            em.persist(new com.example.projectmm.domain.memory.MemoryCompressionEvidence(transitive,
                    com.example.projectmm.domain.memory.MemoryDetailLevel.SCHEMA,dependent));
            // A legacy provenance cycle must terminate safely during cleanup.
            em.persist(new com.example.projectmm.domain.memory.MemoryCompressionEvidence(dependent,
                    com.example.projectmm.domain.memory.MemoryDetailLevel.SCHEMA,transitive));
            em.persist(new com.example.projectmm.domain.memory.MemoryAccessState(source,
                    com.example.projectmm.domain.memory.MemoryAccessLevel.EXPLICIT,story));
            em.persist(new com.example.projectmm.domain.memory.ProtectedMemoryDetail(dependent,"detail",
                    com.example.projectmm.domain.memory.MemoryDetailSalience.HIGH,false));
            em.persist(new com.example.projectmm.infrastructure.memory.MemoryEmbeddingEntity(transitive.getId(),"fingerprint","[1]"));
            em.flush();
            return new long[]{source.getId(),dependent.getId(),transitive.getId(),retained.getId()};
        });
    }
    void assertMemoryDependenciesRemoved(long[] ids) {
        assertThat(memories.findAllById(List.of(ids[0],ids[1],ids[2]))).isEmpty();
        assertThat(memories.findById(ids[3])).isPresent();
        db(() -> {
            var targets = List.of(ids[0],ids[1],ids[2]);
            assertThat(em.createQuery("select count(e) from MemoryCompressionEvidence e where e.memory.id in :ids or e.evidenceMemory.id in :ids",Long.class)
                    .setParameter("ids",targets).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(s) from MemoryAccessState s where s.memory.id in :ids",Long.class)
                    .setParameter("ids",targets).getSingleResult()).isZero();
            assertThat(em.createQuery("select count(d) from ProtectedMemoryDetail d where d.memory.id in :ids",Long.class)
                    .setParameter("ids",targets).getSingleResult()).isZero();
            assertThat(em.find(com.example.projectmm.infrastructure.memory.MemoryEmbeddingEntity.class,ids[2])).isNull();
            return null;
        });
    }
    @Test void briefAndNovelAreDisplayOnlyAndCachedWithoutChangingTimelineOrMemory() {
        enabledAfter(Duration.ofMinutes(20));
        var raw = plan(story,story.plusMinutes(20)).replace("\"kind\":\"WALK\"", "\"kind\":\"WALK\",\"brief\":\"A와 B는 함께 산책했어요! 🍃\"");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        var report = progress.resume(id,login).lastReport();
        var source = db(() -> messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow().getContent());
        var memoryBefore = db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).stream().map(com.example.projectmm.domain.memory.Memory::getSummary).toList());
        var originalJson = db(() -> reports.findById(report.id()).orElseThrow().getActivitiesJson());
        var castBefore = db(() -> sessionService.captureCastState(id));
        assertThat(progress.readStory(id,report.id(),login).briefLogs().getFirst()).contains("10/08 15:00 ~ 15:10", "함께 산책했어요!");
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
        assertThat(memoryBefore.toString()).contains("공원을 산책하며 이야기했다").doesNotContain("함께 산책했어요!");
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenReturn(new WorldRoomTurn(
                "**🍃 이야기**\n*바람이 불었다.*\n\"함께 걸으니 좋네요.\"",new WorldRoomCastChange(List.of(),List.of(a)),List.of(),999));
        var result = progress.generateStory(id,report.id(),login);
        assertThat(result.narrative()).contains("바람이 불었다");
        assertThat(progress.generateStory(id,report.id(),login).narrative()).isEqualTo(result.narrative());
        assertThat(progress.readStory(id,report.id(),login).narrative()).isEqualTo(result.narrative());
        verify(gateway,times(2)).generateWorldRoomTurn(any(),anyString());
        assertThat(db(() -> reports.findById(report.id()).orElseThrow().getActivitiesJson())).isEqualTo(originalJson);
        assertThat(db(() -> messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow().getContent())).isEqualTo(source);
        assertThat(db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).stream().map(com.example.projectmm.domain.memory.Memory::getSummary).toList())).isEqualTo(memoryBefore);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story.plusMinutes(20));
        assertThat(db(() -> sessionService.captureCastState(id))).isEqualTo(castBefore);
    }
    @Test void novelUsesHistoricalSnapshotAndRejectsDuplicateRequestDuringGeneration() {
        enabledAfter(Duration.ofMinutes(20));
        var report = progress.resume(id,login).lastReport();
        db(() -> { sessions.findById(id).orElseThrow().updateSimulatedAt(story.plusDays(2)); return null; });
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenAnswer(call -> {
            com.example.projectmm.domain.dialogue.WorldRoomDialogueContext context=call.getArgument(0);
            assertThat(context.simulatedAt()).isEqualTo(story);
            assertThat(progress.readStory(id,report.id(),login).generating()).isTrue();
            assertThatThrownBy(() -> progress.generateStory(id,report.id(),login)).isInstanceOfSatisfying(ResponseStatusException.class,
                    failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
            assertThat((String)call.getArgument(1)).contains("Core events", "new relationship change", "confirmed memory");
            return new WorldRoomTurn("Saved prose",WorldRoomCastChange.none(),List.of(),0);
        });
        assertThat(progress.generateStory(id,report.id(),login).narrative()).isEqualTo("Saved prose");
        verify(gateway,times(2)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void failedNovelKeepsOriginalAndAllowsExplicitRetry() {
        enabledAfter(Duration.ofMinutes(20));var report=progress.resume(id,login).lastReport();
        var original=db(() -> reports.findById(report.id()).orElseThrow().getActivitiesJson());
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenThrow(new IllegalStateException("fixture failure"));
        assertThatThrownBy(() -> progress.generateStory(id,report.id(),login)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(502));
        var result=progress.readStory(id,report.id(),login);
        assertThat(result.generating()).isFalse();assertThat(result.narrative()).isNull();
        assertThat(result.activities()).isEqualTo(report.activities());
        assertThat(db(() -> reports.findById(report.id()).orElseThrow().getActivitiesJson())).isEqualTo(original);
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenReturn(new WorldRoomTurn("Retry prose",WorldRoomCastChange.none(),List.of(),0));
        assertThat(progress.generateStory(id,report.id(),login).narrative()).isEqualTo("Retry prose");
    }
    @Test void legacyReportUsesEvaluationSnapshotWithoutCurrentContextOrRegenerationOnRead() {
        enabledAfter(Duration.ofMinutes(20));var report=progress.resume(id,login).lastReport();
        db(() -> {reports.findById(report.id()).orElseThrow().captureNarrativeContext(null);return null;});
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenAnswer(call -> {
            com.example.projectmm.domain.dialogue.WorldRoomDialogueContext context=call.getArgument(0);
            assertThat(context.simulatedAt()).isEqualTo(story);
            return new WorldRoomTurn("Legacy prose",WorldRoomCastChange.none(),List.of(),0);
        });
        assertThat(progress.generateStory(id,report.id(),login).narrative()).isEqualTo("Legacy prose");
    }
    @Test void foreignOrDeletedReportCannotGenerateOrReadStory() {
        enabledAfter(Duration.ofMinutes(20));var report=progress.resume(id,login).lastReport();
        assertThatThrownBy(() -> progress.readStory(id,report.id(),"unknown")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> progress.generateStory(id,report.id()+100,"unknown")).isInstanceOf(ResponseStatusException.class);
        progress.discardLatest(id,login,report.id());
        assertThatThrownBy(() -> progress.generateStory(id,report.id(),login)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void deletingReportDuringNovelCallCannotResurrectTimelineOrCache() {
        enabledAfter(Duration.ofMinutes(20));var report=progress.resume(id,login).lastReport();
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenAnswer(call -> {
            progress.discardLatest(id,login,report.id());
            return new WorldRoomTurn("Deleted report prose",WorldRoomCastChange.none(),List.of(),0);
        });
        assertThatThrownBy(() -> progress.generateStory(id,report.id(),login)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));
        assertThat(reports.findById(report.id())).isEmpty();
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id)).isEmpty();
    }
    @Test void legacyReportMissingHistoricalContextNeverUsesFutureRpAsFallback() {
        enabledAfter(Duration.ofMinutes(20));var report=progress.resume(id,login).lastReport();
        db(() -> {
            var original=reports.findById(report.id()).orElseThrow();original.captureNarrativeContext(null);
            variants.findByMessageIdOrderByVariantNoAsc(original.getSourceMessageId()).forEach(v -> v.captureEvaluationContext(null));
            return null;
        });
        assertThatThrownBy(() -> progress.generateStory(id,report.id(),login)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
        assertThat(progress.readStory(id,report.id(),login).activities()).isEqualTo(report.activities());
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={1,12,24})
    void configuredAutoAdvanceLimitBoundsActualTimeline(int hours) {
        assertThat(progress.current(id,login).maximumAutoHours()).isEqualTo(24);
        progress.configure(id,login,true,1,hours);
        when(clock.now()).thenReturn(now.plusDays(2));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(
                plan(story,story.plusHours(hours)),WorldRoomCastChange.none(),List.of(),0));
        var result=progress.resume(id,login);
        assertThat(result.maximumAutoHours()).isEqualTo(hours);
        assertThat(result.simulatedAt()).isEqualTo(story.plusHours(hours));
        assertThat(result.lastReport().to()).isEqualTo(story.plusHours(hours));
        assertThat(result.lastReport().capped()).isTrue();
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(result.simulatedAt());
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void invalidAutoAdvanceLimitCannotChangeSettingsOrPresence() {
        progress.configure(id,login,true,15,12);
        var before=db(() -> sessions.findById(id).orElseThrow().getLastPresenceAt());
        for(var value:List.of(0,25)) assertThatThrownBy(() -> progress.configure(id,login,false,1,value))
                .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        assertThat(progress.current(id,login).enabled()).isTrue();
        assertThat(progress.current(id,login).minimumAbsenceMinutes()).isEqualTo(15);
        assertThat(progress.current(id,login).maximumAutoHours()).isEqualTo(12);
        assertThat(db(() -> sessions.findById(id).orElseThrow().getLastPresenceAt())).isEqualTo(before);
    }
    @Test void novelAutomaticallyBudgetsEpisodesWithoutRewritingCachedProse() {
        enabledAfter(Duration.ofMinutes(20));var report=progress.resume(id,login).lastReport();
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenAnswer(call -> {
            String prompt=call.getArgument(1);
            assertThat(prompt).contains("SCENE-FIRST", "Group related activities", "concrete action", "specific relevant established memory",
                    "1,500–2,500", "2–3", "sleep-only", "AUTOMATIC EPISODE BUDGET").doesNotContain("Each activity starts with a bold heading");
            return new WorldRoomTurn("Scene-based prose",WorldRoomCastChange.none(),List.of(),0);
        });
        var result=progress.generateStory(id,report.id(),login);
        assertThat(progress.generateStory(id,report.id(),login).narrative()).isEqualTo(result.narrative());
        verify(gateway,times(2)).generateWorldRoomTurn(any(),anyString());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,4,7})
    void episodeCountsAutomaticallyChooseMediumLongOrSleepOnlyBudget(int awakeCount) {
        enabledAfter(Duration.ofHours(2));
        var activities=new java.util.ArrayList<PocketActivityPlan.Activity>();
        int count=Math.max(1,awakeCount);
        for(int i=0;i<count;i++) activities.add(new PocketActivityPlan.Activity(story.plusMinutes(i*15),story.plusMinutes((i+1)*15),
                awakeCount==0 ? PocketActivityPlan.Kind.SLEEP : PocketActivityPlan.Kind.REST,List.of(a),"Home","Confirmed activity",false,List.of()));
        String raw="{\"activities\":"+new tools.jackson.databind.ObjectMapper().writeValueAsString(activities)+",\"leaveCharacterIds\":[],\"pauseReason\":null}";
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        var report=progress.resume(id,login).lastReport();
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenAnswer(call -> {
            String prompt=call.getArgument(1);
            assertThat(prompt).contains(awakeCount==0 ? "300–800" : awakeCount==4 ? "3,000–5,000" : "6,000–8,000");
            assertThat(prompt).contains(awakeCount==0 ? "in 1 coherent scenes" : awakeCount==4 ? "in 3–5 coherent scenes" : "in 5–7 coherent scenes");
            return new WorldRoomTurn("Scene prose",WorldRoomCastChange.none(),List.of(),0);
        });
        assertThat(progress.generateStory(id,report.id(),login).narrative()).isEqualTo("Scene prose");
    }
    @Test void legacyCaptionPastEndingsArePoliteWithoutRewritingQuotedDialogue() {
        assertThat(PocketActivityPlan.politeFallback("커피를 마셨다. 바다. 쓰다.")).isEqualTo("커피를 마셨어요. 바다. 쓰다.");
        assertThat(PocketActivityPlan.politeFallback("책을 읽었다. 쉬었다! 밥을 먹었다 🥗")).isEqualTo("책을 읽었어요. 쉬었어요! 밥을 먹었어요 🥗");
        assertThat(PocketActivityPlan.politeFallback("집에 갔다. 잠을 잤다. 돌아왔다.")).isEqualTo("집에 갔어요. 잠을 잤어요. 돌아왔어요.");
        assertThat(PocketActivityPlan.politeFallback("“쉬었다.”라고 말했다.")).isEqualTo("“쉬었다.”라고 말했어요.");
        assertThat(PocketActivityPlan.politeFallback("밀리는 쉬었어요. 오늘은 바다를 보았다.")).isEqualTo("밀리는 쉬었어요. 오늘은 바다를 보았어요.");
    }
    @Test void newRoleplayClosesEarlierUnwrittenStoryWithoutCallingAiOrDeletingOriginal() {
        enabledAfter(Duration.ofMinutes(20));var report=progress.resume(id,login).lastReport();
        db(() -> {messages.save(new WorldRoomMessage(sessions.findById(id).orElseThrow(),ChatRole.USER,"Next roleplay",now.plusHours(1)));return null;});
        assertThatThrownBy(() -> progress.generateStory(id,report.id(),login)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(progress.readStory(id,report.id(),login).activities()).isEqualTo(report.activities());
        assertThat(progress.readStory(id,report.id(),login).generating()).isFalse();
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void originalAndNovelUsageAreSavedPerReportAndReplayDoesNotReuseLatestCall() {
        enabledAfter(Duration.ofMinutes(20));
        var tracker=new com.example.projectmm.infrastructure.dialogue.AiUsageTracker();
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenAnswer(call -> {
            tracker.record(id,"OPENAI","activity-model",100,20,50,150);
            return new WorldRoomTurn(plan(story,story.plusMinutes(20)),WorldRoomCastChange.none(),List.of(),0);
        });
        var report=progress.resume(id,login).lastReport();
        assertThat(progress.readStory(id,report.id(),login).originalUsage().path("model").asText()).isEqualTo("activity-model");
        when(gateway.generateWorldRoomTurn(any(),contains("ABSENCE NOVELIZATION"))).thenAnswer(call -> {
            tracker.record(id,"GEMINI","novel-model",200,0,80,280);
            return new WorldRoomTurn("Novel prose",WorldRoomCastChange.none(),List.of(),0);
        });
        var generated=progress.generateStory(id,report.id(),login);
        assertThat(generated.narrativeUsage().path("totalTokens").asLong()).isEqualTo(280);
        tracker.record(id,"OPENAI","unrelated-later-call",5,0,5,10);
        var saved=progress.readStory(id,report.id(),login);
        assertThat(saved.originalUsage().path("totalTokens").asLong()).isEqualTo(150);
        assertThat(saved.narrativeUsage().path("model").asText()).isEqualTo("novel-model");
        assertThat(progress.generateStory(id,report.id(),login).narrativeUsage()).isEqualTo(saved.narrativeUsage());
        verify(gateway,times(2)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void absenceDepartureMetadataCannotChangeUserControlledCastOrInvalidateActivities() {
        db(() -> {sessionService.applyCastChange(id,new WorldRoomCastChange(List.of(a),List.of()));return null;});
        var before=db(() -> sessionService.captureCastState(id));
        enabledAfter(Duration.ofMinutes(20));
        String repeated=plan(story,story.plusMinutes(20)).replace("\"leaveCharacterIds\":[]","\"leaveCharacterIds\":["+a+","+a+",999999]");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(repeated,WorldRoomCastChange.none(),List.of(),0));
        var result=progress.resume(id,login);
        assertThat(result.lastReport()).isNotNull();
        assertThat(result.simulatedAt()).isEqualTo(story.plusMinutes(20));
        assertThat(db(() -> sessionService.captureCastState(id))).isEqualTo(before);
        // Manual user controls still work and are preserved by later absence processing.
        db(() -> {sessionService.applyCastChange(id,new WorldRoomCastChange(List.of(),List.of(a)));return null;});
        var afterManual=db(() -> sessionService.captureCastState(id));
        when(clock.now()).thenReturn(now.plusMinutes(40));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(
                plan(result.simulatedAt(),result.simulatedAt().plusMinutes(20)),WorldRoomCastChange.none(),List.of(),0));
        progress.resume(id,login);
        assertThat(db(() -> sessionService.captureCastState(id))).isEqualTo(afterManual);
    }
    String plan(LocalDateTime from, LocalDateTime to) {
        var mid = from.plus(Duration.between(from,to).dividedBy(2));
        return """
            {"activities":[
              {"start":"%s","end":"%s","kind":"WALK","characterIds":[%d,%d],"location":"Park","summary":"A와 B는 공원을 산책하며 이야기했다."},
              {"start":"%s","end":"%s","kind":"COFFEE","characterIds":[%d,%d],"location":"Home","summary":"집에 돌아와 커피를 마셨다."}],
              "leaveCharacterIds":[],"pauseReason":null}
            """.formatted(from,mid,a,b,mid,to,a,b);
    }
    void enabledAfter(Duration absence) {
        progress.configure(id,login,true,1); when(clock.now()).thenReturn(now.plus(absence));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(plan(story,story.plus(absence)),WorldRoomCastChange.none(),List.of(),0));
    }
    @Test void concurrentReturnWaitsForDialogueCommitAndCannotApplyDuplicateAbsence() throws Exception {
        when(clock.now()).thenAnswer(call -> LocalDateTime.now());
        progress.configure(id,login,true,1);
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return new WorldRoomTurn("reply",WorldRoomCastChange.none(),List.of(),0);
        });
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var chat=workers.submit(() -> dialogue.chat(id,"hello"));
            assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var returned=workers.submit(() -> progress.resume(id,login));
            release.countDown();
            assertThat(chat.get(10,java.util.concurrent.TimeUnit.SECONDS).reply()).isEqualTo("reply");
            assertThat(returned.get(10,java.util.concurrent.TimeUnit.SECONDS).lastReport()).isNull();
            assertThat(progress.resume(id,login).lastReport()).isNull();
            assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
            verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
            assertThat(db(() -> messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow().getContent())).isEqualTo("reply");
        } finally {release.countDown();workers.shutdownNow();}
    }
    @Test void defaultAbsenceStartsAtFifteenMinutesAndPromptPacesEvents() {
        progress.configure(id,login,true);
        assertThat(progress.current(id,login).minimumAbsenceMinutes()).isEqualTo(15);
        when(clock.now()).thenReturn(now.plusMinutes(14));
        assertThat(progress.resume(id,login).lastReport()).isNull();
        verifyNoInteractions(gateway);
        when(clock.now()).thenReturn(now.plusMinutes(29));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(
            plan(story,story.plusMinutes(15)),WorldRoomCastChange.none(),List.of(),0));
        assertThat(progress.resume(id,login).lastReport()).isNotNull();
        verify(gateway).generateWorldRoomTurn(any(),argThat(prompt -> prompt.contains("15-20 minutes")
            && prompt.contains("at most 1 concise")));
        assertThat(PocketActivityPrompt.build(story,story.plusMinutes(20))).contains("at most 1 concise");
        assertThat(PocketActivityPrompt.build(story,story.plusHours(1))).contains("at most 4 concise");
    }
    @Test void configuredThresholdIsPersistedAndBoundaryGeneratesOnlyOnce() {
        progress.configure(id,login,true,10);
        assertThat(progress.current(id,login).minimumAbsenceMinutes()).isEqualTo(10);
        when(clock.now()).thenReturn(now.plusMinutes(9));
        assertThat(progress.resume(id,login).lastReport()).isNull();
        verifyNoInteractions(gateway);
        when(clock.now()).thenReturn(now.plusMinutes(19));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(
            plan(story,story.plusMinutes(10)),WorldRoomCastChange.none(),List.of(),0));
        var report=progress.resume(id,login).lastReport();
        assertThat(report).isNotNull();
        assertThat(progress.resume(id,login).lastReport().id()).isEqualTo(report.id());
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void disablingAfterEnablePreservesAllStoryStateAndSkipsModel() {
        progress.configure(id,login,true,5);
        progress.configure(id,login,false,null);
        when(clock.now()).thenReturn(now.plusDays(2));
        var result=progress.resume(id,login);
        assertThat(result.enabled()).isFalse();
        assertThat(result.simulatedAt()).isEqualTo(story);
        assertThat(result.lastReport()).isNull();
        assertThat(db(() -> List.copyOf(states.findBySessionId(id).orElseThrow().getConditions()))).containsExactly("tired");
        assertThat(db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).size())).isZero();
        verifyNoInteractions(gateway);
    }
    @Test void invalidThresholdDoesNotMutateConfiguration() {
        assertThatThrownBy(() -> progress.configure(id,login,true,0)).isInstanceOf(ResponseStatusException.class);
        assertThat(progress.current(id,login).enabled()).isFalse();
        assertThat(progress.current(id,login).minimumAbsenceMinutes()).isEqualTo(15);
    }
    @Test void returnStoresOrderedActivitiesOnlyInPocketAndReentryDoesNotRepeat() {
        enabledAfter(Duration.ofMinutes(10));
        var result = progress.resume(id,login);
        assertThat(result.lastReport().activities()).extracting(PocketActivityPlan.Activity::kind)
                .containsExactly(PocketActivityPlan.Kind.WALK,PocketActivityPlan.Kind.COFFEE);
        assertThat(result.simulatedAt()).isEqualTo(story.plusMinutes(10));
        assertThat(db(() -> sessions.findById(id).orElseThrow().getLastProgressedAt())).isEqualTo(now.plusMinutes(10));
        db(() -> {
            var player = states.findBySessionId(id).orElseThrow();
            assertThat(player.getLocation()).isEqualTo("Home");
            assertThat(player.getInventory()).containsExactly("phone");
            assertThat(player.getConditions()).containsExactly("tired");
            var source = messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow();
            assertThat(source.getAbsenceReportId()).isEqualTo(result.lastReport().id());
            assertThat(source.getAutomaticContinuationNo()).isNull();
            assertThat(memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id)).hasSize(4);
            var variant = variants.findByMessageIdAndVariantNo(source.getId(),1).orElseThrow();
            assertThat(variant.getBeforeSimulatedAt()).isEqualTo(story);
            assertThat(variant.getAfterSimulatedAt()).isEqualTo(story.plusMinutes(10));
            assertThat(variant.getEvaluationContextJson()).contains("ABSENCE");
            return null;
        });
        assertThat(progress.resume(id,login).lastReport().id()).isEqualTo(result.lastReport().id());
        assertThat(db(() -> sessions.findById(id).orElseThrow().getLastProgressedAt())).isEqualTo(now.plusMinutes(10));
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void disabledNeverCallsModelAndEnablingStartsNewAnchor() {
        when(clock.now()).thenReturn(now.plusDays(2));
        assertThat(progress.resume(id,login).lastReport()).isNull();
        assertThat(progress.configure(id,login,true).enabled()).isTrue();
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(story);
        verifyNoInteractions(gateway);
    }
    @Test void identicalConfigurationAndLateHeartbeatCannotDiscardAbsence() {
        enabledAfter(Duration.ofMinutes(5));
        progress.configure(id,login,true);
        assertThatThrownBy(() -> progress.presence(id,login)).isInstanceOf(ResponseStatusException.class);
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(story.plusMinutes(5));
    }
    @Test void failureRetainsAnchorAndStoryTimeForRetry() {
        enabledAfter(Duration.ofMinutes(5));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> progress.resume(id,login)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getReason()).contains("시간은 변경되지 않았어요").doesNotContain("secret SQL"));
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(db(() -> sessions.findById(id).orElseThrow().getLastPresenceAt())).isEqualTo(now);
        assertThat(progress.current(id,login).processing()).isFalse();
        doReturn(new WorldRoomTurn(plan(story,story.plusMinutes(5)),WorldRoomCastChange.none(),List.of(),0)).when(gateway).generateWorldRoomTurn(any(),anyString());
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(story.plusMinutes(5));
    }
    @Test void unregisteredParticipantRejectsWholePlanWithoutTimeOrMemoryChange() {
        enabledAfter(Duration.ofMinutes(5));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(plan(story,story.plusMinutes(5)).replace("["+a+","+b+"]","[999999]"), WorldRoomCastChange.none(),List.of(),0));
        assertThatThrownBy(() -> progress.resume(id,login)).isInstanceOf(ResponseStatusException.class);
        assertThat(progress.current(id,login).lastReport()).isNull();
        assertThat(db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).size())).isZero();
    }
    @Test void elapsedIsCappedAndLegacyPauseCannotPermanentlyStopAutonomy() {
        progress.configure(id,login,true,1); when(clock.now()).thenReturn(now.plusDays(8));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(plan(story,story.plusDays(1)),WorldRoomCastChange.none(),List.of(),0));
        var capped = progress.resume(id,login);
        assertThat(capped.lastReport().capped()).isTrue();
        assertThat(capped.simulatedAt()).isEqualTo(story.plusDays(1));
        progress.configure(id,login,false); progress.configure(id,login,true);
        when(clock.now()).thenReturn(now.plusDays(8).plusMinutes(10));
        var paused = plan(story.plusDays(1),story.plusDays(1).plusMinutes(2)).replace("\"pauseReason\":null", "\"pauseReason\":\"플레이어의 선택을 기다립니다.\"");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(paused,WorldRoomCastChange.none(),List.of(),0));
        var result = progress.resume(id,login);
        assertThat(result.simulatedAt()).isEqualTo(story.plusDays(1).plusMinutes(10));
        assertThat(result.pauseReason()).isNull();
        assertThat(result.lastReport().pauseReason()).isNull();
        when(clock.now()).thenReturn(now.plusDays(8).plusMinutes(15));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(
                plan(result.simulatedAt(),result.simulatedAt().plusMinutes(5)),WorldRoomCastChange.none(),List.of(),0));
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(result.simulatedAt().plusMinutes(5));
        verify(gateway,times(3)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void leaseBlocksDuplicateResumeChatAndSettingsUntilModelReturns() {
        enabledAfter(Duration.ofMinutes(5));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenAnswer(call -> {
            assertThat(progress.current(id,login).processing()).isTrue();
            assertThatThrownBy(() -> progress.resume(id,login)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> dialogue.chat(id,"hello")).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> sessionService.updatePocketSettings(id,login,"","",false)).isInstanceOf(ResponseStatusException.class);
            return new WorldRoomTurn(plan(story,story.plusMinutes(5)),WorldRoomCastChange.none(),List.of(),0);
        });
        assertThat(progress.resume(id,login).lastReport()).isNotNull();
    }
    @Test void timelineDeletionRestoresTimeAndRemovesDerivedReportMemories() {
        enabledAfter(Duration.ofMinutes(5)); progress.resume(id,login);
        var source = db(() -> messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow().getId());
        assertThatThrownBy(() -> dialogue.reroll(id,source)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> dialogue.editMessage(id,source,"altered")).isInstanceOf(ResponseStatusException.class);
        var dependencyIds = seedMemoryDependencies();
        dialogue.deleteMessageAndFollowing(id,source);
        assertMemoryDependenciesRemoved(dependencyIds);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(progress.current(id,login).lastReport()).isNull();
        assertThat(db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).size())).isZero();
    }
    @Test void deletingRoomRemovesAbsenceReportsAndMemoriesBeforeSourceMessages() {
        enabledAfter(Duration.ofMinutes(5)); progress.resume(id,login);
        var roomId = db(() -> sessions.findById(id).orElseThrow().getRoom().getId());
        var dependencyIds = seedMemoryDependencies();
        roomService.deleteRoom(roomId,login);
        assertMemoryDependenciesRemoved(dependencyIds);
        assertThat(sessions.existsById(id)).isFalse();
        assertThat(reports.findTopBySessionIdOrderByIdDesc(id)).isEmpty();
        assertThat(memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id)).isEmpty();
    }
    @Test void serverIgnoresPlayerAndSystemMetadataOnAbsenceResponse() {
        enabledAfter(Duration.ofMinutes(5));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(
                plan(story,story.plusMinutes(5)), new WorldRoomCastChange(List.of(999L),List.of(a)),
                List.of(new WorldRoomSystemChange(999L,100)), 99999,
                "Dangerous place", List.of("injured"), List.of("tired"), List.of("weapon"), List.of("phone")));
        progress.resume(id,login);
        db(() -> {
            var player = states.findBySessionId(id).orElseThrow();
            assertThat(player.getLocation()).isEqualTo("Home");
            assertThat(player.getConditions()).containsExactly("tired");
            assertThat(player.getInventory()).containsExactly("phone");
            assertThat(sessions.findById(id).orElseThrow().getSimulatedAt()).isEqualTo(story.plusMinutes(5));
            return null;
        });
    }
    @Test void preparationDatabaseFailureIsSanitizedAndDoesNotConsumeAbsence() {
        enabledAfter(Duration.ofMinutes(5));
        when(recall.recallForWorldRoomSession(anyLong(),anyLong(),any(),anyInt(),anyString()))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("update memories secret SQL"));
        // At least one active character is needed for the real context's recall path.
        db(() -> { sessionService.applyCastChange(id,new WorldRoomCastChange(List.of(a),List.of())); return null; });
        assertThatThrownBy(() -> progress.resume(id,login))
                .isInstanceOfSatisfying(ResponseStatusException.class,error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(503);
                    assertThat(error.getReason()).contains("부재 시간은 그대로").doesNotContain("SQL", "update memories");
                });
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(progress.current(id,login).processing()).isFalse();
        assertThat(db(() -> sessions.findById(id).orElseThrow().getLastPresenceAt())).isEqualTo(now);
        verifyNoInteractions(gateway);
    }
    @Test void playerParticipatesAndLocationPatchPreservesOtherStateAndDeletionRestoresIt() {
        enabledAfter(Duration.ofMinutes(5));
        var raw = plan(story,story.plusMinutes(5)).replace("\"summary\":", "\"playerInvolved\":true,\"summary\":")
                .replace("\"pauseReason\":null", "\"pauseReason\":null,\"playerState\":{\"location\":\"Cafe\"}")
                .replace("\"location\":\"Home\"", "\"location\":\"Cafe\"");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        var report = progress.resume(id,login).lastReport();
        assertThat(report.activities()).allMatch(PocketActivityPlan.Activity::playerInvolved);
        db(() -> {
            var state = states.findBySessionId(id).orElseThrow();
            assertThat(state.getLocation()).isEqualTo("Cafe");
            assertThat(state.getInventory()).containsExactly("phone");
            assertThat(state.getConditions()).containsExactly("tired");
            var source = messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow();
            assertThat(source.getContent()).contains("🚶", "☕", "Player");
            var variant = variants.findByMessageIdAndVariantNo(source.getId(),1).orElseThrow();
            assertThat(variant.getEvaluationContextJson()).contains("ABSENCE AUTONOMY", "Student");
            return null;
        });
        var sourceId = db(() -> messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow().getId());
        dialogue.deleteMessageAndFollowing(id,sourceId);
        assertThat(db(() -> states.findBySessionId(id).orElseThrow().getLocation())).isEqualTo("Home");
    }
    @Test void legacyPausedSessionResumesWithoutTogglingTimeCheck() {
        enabledAfter(Duration.ofMinutes(5));
        db(() -> {
            var session = sessions.findById(id).orElseThrow();
            session.beginProgress("old",now); session.finishProgress("old","플레이어 응답 대기",null);
            return null;
        });
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(story.plusMinutes(5));
        assertThat(db(() -> sessions.findById(id).orElseThrow().getProgressPauseReason())).isNull();
    }
    @Test void npcOnlyActivityCannotOverwritePlayerState() {
        enabledAfter(Duration.ofMinutes(5));
        var raw = plan(story,story.plusMinutes(5)).replace("\"pauseReason\":null",
                "\"pauseReason\":null,\"playerState\":{\"inventory\":[]}");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        assertThatThrownBy(() -> progress.resume(id,login)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getReason()).contains("시간은 변경되지 않았어요").doesNotContain("secret SQL"));
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(db(() -> List.copyOf(states.findBySessionId(id).orElseThrow().getInventory()))).containsExactly("phone");
    }
    @Test void playerParticipationGroundsFinalLocationWhenPatchIsOmitted() {
        enabledAfter(Duration.ofMinutes(5));
        var raw = plan(story,story.plusMinutes(5)).replace("\"summary\":", "\"playerInvolved\":true,\"summary\":")
                .replace("\"location\":\"Home\"", "\"location\":\"Cafe\"");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        progress.resume(id,login);
        assertThat(db(() -> states.findBySessionId(id).orElseThrow().getLocation())).isEqualTo("Cafe");
    }
    @Test void duplicateFinalLocationCannotRejectPlanOrOverrideLastPlayerActivity() {
        enabledAfter(Duration.ofMinutes(5));
        var raw = plan(story,story.plusMinutes(5)).replace("\"summary\":", "\"playerInvolved\":true,\"summary\":")
                .replace("\"pauseReason\":null", "\"pauseReason\":null,\"playerState\":{\"location\":\"Elsewhere\"}");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(story.plusMinutes(5));
        db(() -> {
            var state = states.findBySessionId(id).orElseThrow();
            assertThat(state.getLocation()).isEqualTo("Home");
            assertThat(state.getInventory()).containsExactly("phone");
            assertThat(state.getConditions()).containsExactly("tired");
            return null;
        });
    }
    @Test void legacyReportsWithMissingOrNullPlayerParticipationDoNotBlockTimeCheck() {
        for (var flag : List.of("", "\"playerInvolved\":null,", "\"playerInvolved\":true,")) {
            var raw = "[{\"start\":\"" + story + "\",\"end\":\"" + story.plusMinutes(1)
                    + "\",\"kind\":\"TALK\",\"characterIds\":[" + a
                    + "]," + flag + "\"location\":\"Home\",\"summary\":\"상담을 진행했다.\"}]";
            db(() -> { reports.save(new PocketActivityReport(sessions.findById(id).orElseThrow(),story,
                    story.plusMinutes(1),now,false,null,raw)); return null; });
            var status = progress.current(id,login);
            assertThat(status.lastReport().activities().getFirst().playerInvolved()).isEqualTo(flag.contains("true"));
            assertThat(progress.configure(id,login,true).enabled()).isTrue();
            assertThat(progress.resume(id,login).lastReport().id()).isEqualTo(status.lastReport().id());
            progress.configure(id,login,false);
        }
    }
    @Test void simulatedHoursUseNormalActivityStorageWithoutChangingWallClock() {
        progress.configure(id,login,true);
        var expected = story;
        for (int hours : List.of(1,2,3)) {
            var end = expected.plusHours(hours);
            when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(plan(expected,end),WorldRoomCastChange.none(),List.of(),0));
            var result = progress.simulateAbsence(id,login,hours);
            assertThat(result.simulatedAt()).isEqualTo(end);
            assertThat(result.lastReport().from()).isEqualTo(expected);
            assertThat(result.lastReport().to()).isEqualTo(end);
            assertThat(result.processing()).isFalse();
            expected = end;
        }
        verify(gateway,times(3)).generateWorldRoomTurn(any(),anyString());
        assertThat(db(() -> sessions.findById(id).orElseThrow().getLastPresenceAt())).isEqualTo(now);
    }
    @Test void simulationRequiresEnabledOwnerAndSupportedHours() {
        assertThatThrownBy(() -> progress.simulateAbsence(id,login,1)).isInstanceOf(ResponseStatusException.class);
        progress.configure(id,login,true);
        for(int hours : List.of(0,4,-1))
            assertThatThrownBy(() -> progress.simulateAbsence(id,login,hours)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> progress.simulateAbsence(id,"unknown",1)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(gateway);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
    }
    @Test void failedSimulationKeepsStoryAndCanRetrySameHours() {
        progress.configure(id,login,true);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> progress.simulateAbsence(id,login,2)).isInstanceOf(ResponseStatusException.class);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        doReturn(new WorldRoomTurn(plan(story,story.plusHours(2)),WorldRoomCastChange.none(),List.of(),0)).when(gateway).generateWorldRoomTurn(any(),anyString());
        assertThat(progress.simulateAbsence(id,login,2).simulatedAt()).isEqualTo(story.plusHours(2));
    }
    @Test void invalidModelTimesAreRepairedWithinRequestedIntervalInStoryOrder() {
        enabledAfter(Duration.ofHours(3));
        var mid = story.plusMinutes(90);
        var raw = plan(story,story.plusHours(3)).replace(story.toString(),story.minusSeconds(1).toString())
                .replace(mid.toString(),story.plusHours(4).toString());
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        var result = progress.resume(id,login);
        var activities = result.lastReport().activities();
        assertThat(result.simulatedAt()).isEqualTo(story.plusHours(3));
        assertThat(activities.getFirst().start()).isEqualTo(story);
        assertThat(activities.getFirst().end()).isEqualTo(activities.getLast().start());
        assertThat(activities.getLast().end()).isEqualTo(story.plusHours(3));
        assertThat(activities).extracting(PocketActivityPlan.Activity::kind)
                .containsExactly(PocketActivityPlan.Kind.WALK,PocketActivityPlan.Kind.COFFEE);
        assertThat(activities).allMatch(activity -> activity.end().isAfter(activity.start()));
    }
    @Test void malformedModelDateUsesClaimedIntervalAndDoesNotApplyTwice() {
        progress.configure(id,login,true);
        var raw=plan(story,story.plusHours(1)).replace(story.plusHours(1).toString(),"2026-10-46:00");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        var result=progress.simulateAbsence(id,login,1);
        var activities=result.lastReport().activities();
        assertThat(activities.getFirst().start()).isEqualTo(story);
        assertThat(activities.getFirst().end()).isEqualTo(activities.getLast().start());
        assertThat(activities.getLast().end()).isEqualTo(story.plusHours(1));
        assertThat(result.simulatedAt()).isEqualTo(story.plusHours(1));
        assertThat(progress.resume(id,login).lastReport().id()).isEqualTo(result.lastReport().id());
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void missingPlayerModelBlocksEnableAndPreservesConfiguration() {
        db(()->{sessionService.pocket(id,login).getPlayerProfile().updateModel(null,null);return null;});
        assertThatThrownBy(()->progress.configure(id,login,true,30)).hasMessageContaining("플레이어 모델링");
        assertThat(progress.current(id,login).enabled()).isFalse();
        assertThat(progress.current(id,login).minimumAbsenceMinutes()).isEqualTo(15);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        verifyNoInteractions(gateway);
    }
    @Test void missingRegisteredNpcModelBlocksEnableEvenWhenNpcIsOffScene() {
        db(()->{characters.findById(b).orElseThrow().updateModel(null,null);return null;});
        assertThatThrownBy(()->progress.configure(id,login,true)).hasMessageContaining("캐릭터 모델링");
        assertThat(progress.current(id,login).enabled()).isFalse();
        verifyNoInteractions(gateway);
    }
    @Test void modelRemovedAfterEnableBlocksResumeAndCheatButOffRemainsAvailable() {
        enabledAfter(Duration.ofHours(1));
        db(()->{characters.findById(a).orElseThrow().updateModel(null,null);return null;});
        assertThatThrownBy(()->progress.resume(id,login)).hasMessageContaining("캐릭터 모델링");
        assertThatThrownBy(()->progress.simulateAbsence(id,login,1)).hasMessageContaining("캐릭터 모델링");
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(progress.current(id,login).lastReport()).isNull();
        assertThat(progress.configure(id,login,false).enabled()).isFalse();
        verifyNoInteractions(gateway);
    }
    @Test void modelRemovedDuringGenerationPreventsApplyingThePlan() {
        progress.configure(id,login,true);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenAnswer(call->{
            db(()->{characters.findById(a).orElseThrow().updateModel(null,null);return null;});
            return new WorldRoomTurn(plan(story,story.plusHours(1)),WorldRoomCastChange.none(),List.of(),0);
        });
        assertThatThrownBy(()->progress.simulateAbsence(id,login,1)).hasMessageContaining("캐릭터 모델링").isInstanceOf(ResponseStatusException.class);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(progress.current(id,login).lastReport()).isNull();
    }
    @Test void newActivityAnimationsAndInteractionMetadataSurviveStorageAndTimeRepair() {
        progress.configure(id,login,true);
        var raw="""
            {"activities":[{"start":"broken","end":"broken","kind":"RUN","characterIds":[%d,%d],
            "location":"Park","summary":"두 사람은 달렸다.","interaction":"CLOSENESS","interactionActorIds":["%d","%d"],
            "actorCues":[{"actorId":"%d","emojis":["🏃"],"pose":"ARMS_CROSSED"}]},
            {"start":"broken","end":"broken","kind":"DRINK","characterIds":[%d,%d],
            "location":"Home","summary":"두 사람은 음료를 마셨다.","interaction":"AFFECTION","interactionActorIds":["%d","%d"]}],
            "leaveCharacterIds":[],"pauseReason":null}
            """.formatted(a,b,a,b,a,a,b,a,b);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        progress.simulateAbsence(id,login,1);
        var saved=progress.current(id,login).lastReport().activities();
        assertThat(saved.getFirst().kind()).isEqualTo(PocketActivityPlan.Kind.RUN);
        assertThat(saved.getFirst().interaction()).isEqualTo("CLOSENESS");
        assertThat(saved.getFirst().interactionActorIds()).containsExactly(a.toString(),b.toString());
        assertThat(saved.getFirst().actorCues().getFirst().pose()).isEqualTo("ARMS_CROSSED");
        assertThat(saved.getLast().kind()).isEqualTo(PocketActivityPlan.Kind.DRINK);
        assertThat(saved.getLast().interaction()).isEqualTo("AFFECTION");
        assertThat(saved.getLast().end()).isEqualTo(story.plusHours(1));
    }
    @Test void interactionCannotTargetAnAbsentOrUnregisteredCharacter() {
        progress.configure(id,login,true);
        var raw=plan(story,story.plusHours(1)).replace("\"kind\":\"WALK\"", "\"kind\":\"WALK\",\"interaction\":\"INTIMACY\",\"interactionActorIds\":[\"99999999\",\""+a+"\"]");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        var result=progress.simulateAbsence(id,login,1);
        assertThat(result.lastReport().activities().getFirst().interaction()).isEqualTo("NONE");
    }
    @Test void malformedDateDoesNotBypassParticipantValidationOrChangeStoryTime() {
        progress.configure(id,login,true);
        var raw=plan(story,story.plusHours(1)).replace(story.plusHours(1).toString(),"2026-10-46:00")
                .replace("[%d,%d]".formatted(a,b),"[999999999]");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        assertThatThrownBy(()->progress.simulateAbsence(id,login,1)).isInstanceOf(ResponseStatusException.class);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(progress.current(id,login).lastReport()).isNull();
    }
    @Test void soloPlayerCanContinueAtHomeWithoutBringingCounsellorAlong() {
        progress.configure(id,login,true);
        db(() -> { sessionService.applyCastChange(id,new WorldRoomCastChange(List.of(a),List.of())); return null; });
        var raw = """
            {"activities":[{"start":"%s","end":"%s","kind":"REST","characterIds":[],
            "playerInvolved":true,"location":"Home","summary":"플레이어는 상담을 마치고 집에서 자신의 저녁을 보냈다."}],
            "leaveCharacterIds":[%d],"pauseReason":null}
            """.formatted(story,story.plusHours(1),a);
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        var result = progress.simulateAbsence(id,login,1);
        assertThat(db(() -> sessionService.captureCastState(id))).anyMatch(cast -> cast.characterId().equals(a) && cast.active());
        assertThat(result.lastReport().activities().getFirst().characterIds()).isEmpty();
        assertThat(result.lastReport().activities().getFirst().playerInvolved()).isTrue();
        assertThat(db(() -> states.findBySessionId(id).orElseThrow().getLocation())).isEqualTo("Home");
        assertThat(db(() -> messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow().getContent())).contains("Player", "🛋️");
        assertThat(db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).size())).isZero();
        verify(gateway).generateWorldRoomTurn(any(),argThat(prompt -> prompt.contains("COMPLETED EVENTS STAY COMPLETED")
                && prompt.contains("PRIMARY VIEWPOINT") && prompt.contains("after a shared meal ends")));
    }
    @Test void rejectingLatestAbsenceRestoresTimelineAndRemovesDerivedMemoryWithoutGeneratingAgain() {
        enabledAfter(Duration.ofMinutes(5));
        var report = progress.resume(id,login).lastReport();
        var dependencyIds = seedMemoryDependencies();
        var result = progress.discardLatest(id,login,report.id());
        assertMemoryDependenciesRemoved(dependencyIds);
        assertThat(result.simulatedAt()).isEqualTo(story);
        assertThat(result.lastReport()).isNull();
        assertThat(db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id))).isEmpty();
        assertThat(progress.resume(id,login).simulatedAt()).isEqualTo(story);
        verify(gateway,times(1)).generateWorldRoomTurn(any(),anyString());
    }
    @Test void rejectingOldOrForeignReportCannotRemoveNewerActivityOrConversation() {
        enabledAfter(Duration.ofMinutes(5));
        var report = progress.resume(id,login).lastReport();
        assertThatThrownBy(() -> progress.discardLatest(id,"unknown",report.id())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> progress.discardLatest(id,login,report.id()+100)).isInstanceOf(ResponseStatusException.class);
        db(() -> { messages.save(new WorldRoomMessage(sessions.findById(id).orElseThrow(),ChatRole.USER,"newer user turn",now)); return null; });
        assertThatThrownBy(() -> progress.discardLatest(id,login,report.id())).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
        assertThat(progress.current(id,login).lastReport().id()).isEqualTo(report.id());
        assertThat(db(() -> messages.findTopBySessionIdOrderByIdDesc(id).orElseThrow().getContent())).isEqualTo("newer user turn");
    }
    @Test void actorVisualCuesSurviveStorageAndReplayWithoutChangingPlayerState() {
        enabledAfter(Duration.ofMinutes(5));
        var cue = "\"playerInvolved\":true,\"actorCues\":[{\"actorId\":\"player\",\"emojis\":[\"🤔\",\"💤\"],\"emotion\":\"CALM\",\"turn\":0},{\"actorId\":\""+a+"\",\"emojis\":[\"🛏️\",\"✔️\"],\"emotion\":\"HAPPY\",\"turn\":1}],";
        var raw = plan(story,story.plusMinutes(5)).replace("\"summary\":",cue+"\"summary\":");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(raw,WorldRoomCastChange.none(),List.of(),0));
        progress.resume(id,login);
        var cues = progress.current(id,login).lastReport().activities().getFirst().actorCues();
        assertThat(cues).hasSize(2);
        assertThat(cues.getFirst().emojis()).containsExactly("🤔","💤");
        assertThat(cues.getLast().emotion()).isEqualTo("HAPPY");
        assertThat(db(() -> List.copyOf(states.findBySessionId(id).orElseThrow().getConditions()))).containsExactly("tired");
    }
    @Test void otherUserCannotReadOrChangeProgress() {
        assertThatThrownBy(() -> progress.resume(id,"unknown")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> progress.configure(id,"unknown",true)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(gateway);
    }
}
