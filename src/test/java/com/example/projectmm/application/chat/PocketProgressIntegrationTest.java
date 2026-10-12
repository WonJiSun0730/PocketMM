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
            var first = new Character("A"); first.assignWorld(world); a = characters.save(first).getId();
            var second = new Character("B"); second.assignWorld(world); b = characters.save(second).getId();
            var player = players.save(new PlayerProfile(number, "Player", "Student", null));
            var session = sessionService.start(room.getId(), login, player.getId(), intro.getId());
            id = session.getId(); session.updateSimulatedAt(story);
            states.findBySessionId(id).orElseThrow().update("Home", List.of("tired"), List.of("phone"));
            return null;
        });
        now = LocalDateTime.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        when(clock.now()).thenReturn(now);
    }
    @AfterEach void cleanup() { if (sessions.existsById(id)) sessionService.deletePocket(id, login); }
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
        progress.configure(id,login,true); when(clock.now()).thenReturn(now.plus(absence));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(plan(story,story.plus(absence)),WorldRoomCastChange.none(),List.of(),0));
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
        progress.configure(id,login,true); when(clock.now()).thenReturn(now.plusDays(8));
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(plan(story,story.plusDays(3)),WorldRoomCastChange.none(),List.of(),0));
        var capped = progress.resume(id,login);
        assertThat(capped.lastReport().capped()).isTrue();
        assertThat(capped.simulatedAt()).isEqualTo(story.plusDays(3));
        progress.configure(id,login,false); progress.configure(id,login,true);
        when(clock.now()).thenReturn(now.plusDays(8).plusMinutes(10));
        var paused = plan(story.plusDays(3),story.plusDays(3).plusMinutes(2)).replace("\"pauseReason\":null", "\"pauseReason\":\"플레이어의 선택을 기다립니다.\"");
        when(gateway.generateWorldRoomTurn(any(),anyString())).thenReturn(new WorldRoomTurn(paused,WorldRoomCastChange.none(),List.of(),0));
        var result = progress.resume(id,login);
        assertThat(result.simulatedAt()).isEqualTo(story.plusDays(3).plusMinutes(10));
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
        dialogue.deleteMessageAndFollowing(id,source);
        assertThat(progress.current(id,login).simulatedAt()).isEqualTo(story);
        assertThat(progress.current(id,login).lastReport()).isNull();
        assertThat(db(() -> memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(id).size())).isZero();
    }
    @Test void deletingRoomRemovesAbsenceReportsAndMemoriesBeforeSourceMessages() {
        enabledAfter(Duration.ofMinutes(5)); progress.resume(id,login);
        var roomId = db(() -> sessions.findById(id).orElseThrow().getRoom().getId());
        roomService.deleteRoom(roomId,login);
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
    @Test void otherUserCannotReadOrChangeProgress() {
        assertThatThrownBy(() -> progress.resume(id,"unknown")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> progress.configure(id,"unknown",true)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(gateway);
    }
}
