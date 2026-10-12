package com.example.projectmm.application.chat;

import com.example.projectmm.application.dialogue.*;
import com.example.projectmm.application.simulation.CatchUpSimulationService;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.dialogue.*;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.memory.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** Claims commit before model calls; applying a plan and its timeline is atomic. */
@Service @RequiredArgsConstructor
public class PocketProgressService {
    private final WorldRoomSessionService sessionService;
    private final WorldRoomDialogueService dialogue;
    private final WorldRoomSessionRepository sessions;
    private final PocketActivityReportRepository reports;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomMessageVariantRepository variants;
    private final WorldRoomPlayerStateRepository playerStates;
    private final CharacterRepository characters;
    private final MemoryRepository memories;
    private final WorldRoomContextBuilder contexts;
    private final DialogueGateway gateway;
    private final CatchUpSimulationService simulation;
    private final PresenceClock clock;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager em;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PocketProgressService.class);

    public record Report(Long id, LocalDateTime from, LocalDateTime to, LocalDateTime processedAt,
            boolean capped, String pauseReason, List<PocketActivityPlan.Activity> activities) {}
    public record Status(boolean enabled, LocalDateTime simulatedAt, String pauseReason, Report lastReport, boolean processing) {}
    private record Claim(Long sessionId, String token, LocalDateTime from, LocalDateTime to,
            boolean capped, WorldRoomDialogueContext context) {}
    private record Prepared(Status status, Claim claim) {}

    private <T> T tx(java.util.function.Supplier<T> work) {
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(ignored -> work.get());
    }
    private WorldRoomSession owned(Long id, String loginId, boolean lock) {
        sessionService.pocket(id, loginId);
        var session = lock ? sessions.lockById(id).orElseThrow() : sessions.findById(id).orElseThrow();
        if (lock) em.refresh(session);
        return session;
    }
    private void idle(WorldRoomSession session) {
        if (session.progressInProgress(clock.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "부재 활동을 정리 중이에요. 잠시 기다려 주세요.");
    }
    private Status status(WorldRoomSession session) {
        var report = reports.findTopBySessionIdOrderByIdDesc(session.getId()).map(r -> {
            var activities = JSON.readValue(r.getActivitiesJson(), PocketActivityPlan.Activity[].class);
            return new Report(r.getId(), r.getFromTime(), r.getToTime(), r.getProcessedAt(), r.isCapped(),
                    r.getPauseReason(), List.of(activities));
        }).orElse(null);
        return new Status(session.isTimeCheckEnabled(), session.getSimulatedAt(), null,
                report, session.progressInProgress(clock.now()));
    }
    public Status current(Long id, String loginId) {
        return tx(() -> status(owned(id, loginId, false)));
    }
    public Status discardLatest(Long id, String loginId, Long reportId) {
        return tx(() -> {
            var session = owned(id,loginId,true); idle(session);
            var report = reports.findTopBySessionIdOrderByIdDesc(id)
                    .filter(item -> Objects.equals(item.getId(),reportId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"최근 부재 활동이 바뀌었어요. 다시 확인해 주세요."));
            var latest = messages.findTopBySessionIdOrderByIdDesc(id).orElse(null);
            if (report.getSourceMessageId() == null || latest == null
                    || !Objects.equals(latest.getId(),report.getSourceMessageId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"이 활동 이후에 대화가 진행됐어요. 대화 기록에서 해당 시점부터 삭제해 주세요.");
            // Discarding is an explicit present-time choice, not another absence to simulate.
            session.markPresent(clock.now());
            dialogue.deleteMessageAndFollowing(id,report.getSourceMessageId());
            em.flush();
            return status(session);
        });
    }
    public Status configure(Long id, String loginId, boolean enabled) {
        return tx(() -> {
            var session = owned(id, loginId, true); idle(session);
            if (enabled && session.getPlayerProfile() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "플레이어를 선택한 후 시간 체크를 켜 주세요.");
            // An identical request must never erase an outstanding absence.
            if (session.isTimeCheckEnabled() != enabled) session.configureTimeCheck(enabled, clock.now());
            return status(session);
        });
    }
    public void presence(Long id, String loginId) {
        tx(() -> {
            var session = owned(id, loginId, true); idle(session);
            // A delayed heartbeat must not silently discard an unprocessed absence.
            if (session.isTimeCheckEnabled() && session.getLastPresenceAt() != null
                    && Duration.between(session.getLastPresenceAt(), clock.now()).getSeconds() >= 60)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "먼저 부재 활동을 확인해 주세요.");
            session.markPresent(clock.now()); return null;
        });
    }
    public Status resume(Long id, String loginId) { return resume(id, loginId, null); }
    public Status simulateAbsence(Long id, String loginId, int hours) {
        if (hours < 1 || hours > 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "테스트 시간은 1·2·3시간 중 선택해 주세요.");
        return resume(id, loginId, Duration.ofHours(hours));
    }
    private Status resume(Long id, String loginId, Duration testElapsed) {
        Prepared prepared;
        try {
            prepared = tx(() -> {
                var session = owned(id, loginId, true); idle(session);
                if (testElapsed != null && (!session.isTimeCheckEnabled() || session.getPlayerProfile() == null))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "플레이어를 선택하고 시간 체크를 켠 후 테스트해 주세요.");
                session.clearProgressPause();
                var now = clock.now(); var presence = session.getLastPresenceAt();
                if (!session.isTimeCheckEnabled() || session.getPlayerProfile() == null
                        || (testElapsed == null && (presence == null
                        || Duration.between(presence, now).getSeconds() < 60))) {
                    session.markPresent(now); return new Prepared(status(session), null);
                }
                var elapsed = testElapsed == null ? Duration.between(presence, now) : testElapsed;
                var interval = simulation.calculate(session.getSimulatedAt(), session.getSimulatedAt().plus(elapsed));
                var context = contexts.buildSession(id);
                var token = UUID.randomUUID().toString(); session.beginProgress(token, now);
                return new Prepared(null, new Claim(id, token, interval.from(), interval.to(), interval.capped(), context));
            });
        } catch (ResponseStatusException known) {
            throw known;
        } catch (RuntimeException failure) {
            // Preparation rolls back before any claim or story time is committed.
            // Never expose raw database statements through the time-check panel.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "기억 정보를 정리하지 못했어요. 부재 시간은 그대로 남아 있어요. 잠시 후 다시 확인해 주세요.", failure);
        }
        if (prepared.claim() == null) return prepared.status();
        var claim = prepared.claim();
        String stage = "GENERATE";
        try {
            var prompt = PocketActivityPrompt.build(claim.from(), claim.to());
            var raw = gateway.generateWorldRoomTurn(claim.context(), prompt).reply();
            stage = "VALIDATE";
            var plan = PocketActivityPlan.parse(raw, claim.context(), claim.from(), claim.to());
            stage = "APPLY";
            return tx(() -> complete(claim, plan, prompt, raw));
        } catch (RuntimeException failure) {
            // The resolver logs only our public 502 message. Keep the actual cause server-side.
            LOG.warn("Pocket absence failed: sessionId={}, stage={}", id, stage, failure);
            tx(() -> {
                var session = sessions.lockById(id).orElse(null);
                if (session != null) {
                    em.refresh(session);
                    if (Objects.equals(session.getProgressOperationId(), claim.token()))
                        session.finishProgress(claim.token(), session.getProgressPauseReason(), null);
                }
                return null;
            });
            if (failure instanceof ResponseStatusException response) throw response;
            String reason = switch (stage) {
                case "VALIDATE" -> "AI가 반환한 부재 활동의 형식이나 시각·상태가 맞지 않아 적용하지 못했어요.";
                case "APPLY" -> "부재 활동을 저장하지 못했어요.";
                default -> "AI의 부재 활동 응답을 받지 못했어요.";
            };
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    reason + " 시간은 변경되지 않았어요. 다시 시도해 주세요.", failure);
        }
    }
    private Status complete(Claim claim, PocketActivityPlan plan, String prompt, String raw) {
        var session = sessions.lockById(claim.sessionId()).orElseThrow(); em.refresh(session);
        if (!Objects.equals(session.getProgressOperationId(), claim.token()) || !session.progressInProgress(clock.now())
                || !Objects.equals(session.getSimulatedAt(), claim.from()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "포켓 상태가 바뀌었어요. 다시 확인해 주세요.");
        var beforeCast = sessionService.captureCastState(session.getId());
        var beforeSystems = sessionService.captureSystemState(session.getId());
        var state = playerStates.findBySessionId(session.getId()).orElse(null);
        var playerSnapshot = new LinkedHashMap<String,Object>();
        playerSnapshot.put("location", state == null ? null : state.getLocation());
        playerSnapshot.put("conditions", state == null ? List.of() : List.copyOf(state.getConditions()));
        playerSnapshot.put("inventory", state == null ? List.of() : List.copyOf(state.getInventory()));
        var playerJson = JSON.writeValueAsString(playerSnapshot);
        var appliedTo = plan.appliedUntil(claim.from(), claim.to());
        session.updateSimulatedAt(appliedTo);
        sessionService.applyCastChange(session.getId(), new WorldRoomCastChange(List.of(), plan.leaveCharacterIds()));
        var report = reports.save(new PocketActivityReport(session, claim.from(), appliedTo, clock.now(),
                claim.capped(), plan.pauseReason(), JSON.writeValueAsString(plan.activities())));
        var names = new HashMap<Long,String>();
        claim.context().roomCast().forEach(c -> names.put(c.characterId(), c.characterName()));
        var summary = new StringBuilder("[부재 활동]\n");
        for (var activity : plan.activities()) {
            summary.append(activity.kind().emoji()).append(" ").append(activity.start()).append(" ~ ").append(activity.end()).append(" · ")
                    .append(activity.characterIds().stream().map(names::get).collect(java.util.stream.Collectors.joining("·")))
                    .append(activity.playerInvolved() ? " · " + claim.context().playerProfile().name() : "")
                    .append(" · ").append(activity.location()).append("\n").append(activity.summary()).append("\n");
        }
        if (plan.activities().isEmpty()) summary.append("별도로 기록할 활동 없이 시간이 지났어요.\n");
        if (plan.pauseReason() != null) summary.append("진행을 멈춘 이유: ").append(plan.pauseReason());
        var source = messages.save(new WorldRoomMessage(session, ChatRole.CHARACTER, summary.toString(), clock.now()));
        source.markAbsenceReport(report.getId()); report.linkMessage(source.getId());
        var variant = variants.save(new WorldRoomMessageVariant(source, 1, summary.toString(), clock.now()));
        sessionService.saveCastSnapshot(variant, beforeCast, sessionService.captureCastState(session.getId()));
        sessionService.saveSystemSnapshot(variant, beforeSystems, sessionService.captureSystemState(session.getId()));
        sessionService.saveTimeSnapshot(variant, claim.from(), appliedTo);
        var patch = plan.playerState();
        if (patch != null) {
            if (state == null) state = playerStates.save(new WorldRoomPlayerState(session));
            state.update(patch.location() == null ? state.getLocation() : patch.location(),
                    patch.conditions() == null ? List.copyOf(state.getConditions()) : patch.conditions(),
                    patch.inventory() == null ? List.copyOf(state.getInventory()) : patch.inventory());
        }
        var afterPlayer = new LinkedHashMap<String,Object>();
        afterPlayer.put("location", state == null ? null : state.getLocation());
        afterPlayer.put("conditions", state == null ? List.of() : List.copyOf(state.getConditions()));
        afterPlayer.put("inventory", state == null ? List.of() : List.copyOf(state.getInventory()));
        variant.capturePlayerStateWorldline(playerJson, JSON.writeValueAsString(afterPlayer));
        variant.captureEvaluationContext(DialogueEvaluationEvidence.capture("ABSENCE", "[부재 활동]", prompt, raw,
                claim.context().aiModel(), claim.context()));
        for (var activity : plan.activities()) for (var participant : activity.characterIds()) {
            var character = characters.findById(participant).orElseThrow();
            memories.save(Memory.fromPocketActivity(session, source, character,
                    activity.location() + " · " + activity.summary(), activity.end()));
        }
        session.markProgressed(clock.now());
        session.finishProgress(claim.token(), plan.pauseReason(), clock.now());
        return status(session);
    }
}
