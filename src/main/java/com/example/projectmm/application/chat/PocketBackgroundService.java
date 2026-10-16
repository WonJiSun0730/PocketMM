package com.example.projectmm.application.chat;

import com.example.projectmm.infrastructure.ai.GeminiBackgroundGenerator;
import com.example.projectmm.infrastructure.scene.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class PocketBackgroundService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PocketBackgroundService.class);
    public record Context(long sessionId, String scope, String worldDescription, String simulatedAt) {
        public Context(long sessionId,String scope,String worldDescription) { this(sessionId,scope,worldDescription,null); }
        public Context at(String value) {
            if (value != null && !value.isBlank()) {
                try { java.time.LocalDateTime.parse(value); }
                catch (java.time.format.DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"장면 시각을 확인해 주세요."); }
            }
            return new Context(sessionId,scope,worldDescription,value);
        }
        public String daypart() { return PocketBackgroundStore.daypart(simulatedAt); }
    }
    public record SceneAsset(String id, String location, String backgroundUrl, String sceneryUrl, String source,
                             int width, int height, String daypart, String simulatedAt) {}
    public record Result(String status, SceneAsset asset, boolean reused, String error, boolean automatic, boolean available) {}
    private static final class Job {
        final String scope, location, daypart; final Instant created = Instant.now();
        volatile String status = "GENERATING", error;
        Job(String scope, String location, String daypart) { this.scope = scope; this.location = location; this.daypart=daypart; }
    }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.example.projectmm.domain.chat.WorldRoomSessionRepository pocketSessions;
    private static final String IMAGE_BLOCKED="이미지 생성은 별도 Gemini 기능입니다. OpenAI 포켓에서는 Gemini 이미지 호출을 차단하며, 저장 이미지와 직접 첨부는 사용할 수 있어요.";
    private boolean imageBlocked(Context context) {
        return pocketSessions!=null && pocketSessions.findById(context.sessionId())
                .map(s->"OPENAI".equalsIgnoreCase(s.getAiProvider())).orElse(true);
    }
    private void requireImageProvider(Context context) {
        if(imageBlocked(context))throw new ResponseStatusException(HttpStatus.CONFLICT,IMAGE_BLOCKED);
    }
    private final WorldRoomSessionService sessions;
    private final PocketBackgroundStore store;
    private final GeminiBackgroundGenerator generator;
    private final boolean enabled;
    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), Thread.ofPlatform().daemon().name("pocket-background-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    public PocketBackgroundService(WorldRoomSessionService sessions, PocketBackgroundStore store,
            GeminiBackgroundGenerator generator, @Value("${pocket.background.enabled:true}") boolean enabled) {
        this.sessions = sessions; this.store = store; this.generator = generator; this.enabled = enabled;
    }
    @Transactional(readOnly = true)
    public Context context(long sessionId, String loginId) {
        var session = sessions.pocket(sessionId, loginId);
        var world = session.getRoom().getWorld();
        String description = world.getName() + "\n" + Objects.toString(world.getDescription(), "");
        if (description.length() > 3000) description = description.substring(0, 3000);
        // Reuse across the same user's Pockets in the same world; never leak private scenes across users.
        return new Context(sessionId, world.getId() + "-" + session.getMember().getId(), description);
    }
    public Result resolve(Context context, String location) {
        location = location(location);
        var catalog = store.catalog(context.scope());
        var asset = store.match(context.scope(), location, context.daypart());
        if (asset.isPresent()) return result(context, "READY", asset.get(), location, null, catalog.automatic());
        Job job = jobs.get(key(context, location));
        String unavailable = imageBlocked(context) ? IMAGE_BLOCKED : !enabled ? "이미지 생성 기능이 서버에서 꺼져 있어요."
                : !generator.available() ? "이미지 생성용 Gemini 연결 설정이 없어요." : null;
        return new Result(job == null ? "MISSING" : job.status, null, false,
                job == null ? unavailable : job.error, catalog.automatic(), enabled && generator.available() && !imageBlocked(context));
    }
    public synchronized Result request(Context context, String requested) {
        requireImageProvider(context);
        String location = location(requested);
        Result current = resolve(context, location);
        if (current.asset() != null || current.status().equals("GENERATING")) return current;
        if (!current.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "서버의 이미지 생성 설정을 확인해 주세요.");
        jobs.entrySet().removeIf(e -> !e.getValue().status.equals("GENERATING") && e.getValue().created.isBefore(Instant.now().minusSeconds(1800)));
        // A similar place already generating shares that task rather than paying twice.
        for (Job job : jobs.values()) if (job.scope.equals(context.scope()) && job.status.equals("GENERATING")
                && job.daypart.equals(context.daypart())
                && PocketBackgroundMatcher.score(location, job.location, "") > 0) {
            jobs.put(key(context, location), job); return resolve(context, location);
        }
        Job job = new Job(context.scope(), location,context.daypart()); String key = key(context, location); jobs.put(key, job);
        try { worker.submit(() -> generate(context, job)); }
        catch (RejectedExecutionException e) {
            jobs.remove(key, job); throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "배경 생성 요청이 많아요. 잠시 후 다시 시도해 주세요.");
        }
        return resolve(context, location);
    }
    private void generate(Context context, Job job) {
        String[] phase = {"CACHE"};
        try {
            store.locked(context.scope(), () -> {
                // Recheck after acquiring the cross-process disk lock, including after a restart.
                if (store.match(context.scope(), job.location, context.daypart()).isPresent()) return null;
                String id = key(context, job.location).substring(key(context, job.location).lastIndexOf(':') + 1);
                byte[] background = store.stagedBackground(context.scope(), id);
                if (background == null) {
                    requireImageProvider(context);
                    phase[0] = "BACKGROUND";
                    background = PocketBackgroundImages.prepare(generator.generate(context.worldDescription() + "\nScene time period: " + context.daypart() + "\nPlace: " + job.location, null, false), false, false);
                    store.stageBackground(context.scope(), id, background);
                }
                requireImageProvider(context);
                phase[0] = "SCENERY";
                byte[] scenery = PocketBackgroundImages.prepare(generator.generate(context.worldDescription() + "\nScene time period: " + context.daypart() + "\nPlace: " + job.location, background, true), true, true);
                phase[0] = "STORE";
                store.complete(context.scope(), id, job.location, "", "AI", scenery, context.simulatedAt());
                return null;
            });
            job.status = "READY";
        } catch (Exception e) {
            // Never send a provider body, key or raw filesystem path to the player.
            job.error = e instanceof ResponseStatusException status ? status.getReason()
                    : e instanceof IOException ? "배경 파일을 저장하지 못했어요. 저장 공간과 권한을 확인해 주세요."
                    : "주머니 안쪽을 준비하지 못했어요. 다시 시도할 수 있어요.";
            // Log safe classification only, never provider bodies, prompts or API credentials.
            log.warn("Pocket background failed: session={}, phase={}, cause={}, reason={}",
                    context.sessionId(), phase[0], e.getClass().getSimpleName(), job.error);
            job.status = "FAILED";
        }
    }
    public Result configure(Context context, boolean automatic, String location) {
        if(automatic)requireImageProvider(context);
        location = location(location);
        try { store.configure(context.scope(), automatic); }
        catch (IOException e) { throw storageError(); }
        return resolve(context, location);
    }
    public Result upload(Context context, String location, String keywords, byte[] background, byte[] scenery) {
        String place = location(location);
        if (keywords != null && keywords.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "키워드는 500자 이하로 입력해 주세요.");
        byte[] bg = PocketBackgroundImages.prepare(background, false, false);
        byte[] overlay = PocketBackgroundImages.prepare(scenery, true, false);
        String id = UUID.randomUUID().toString().replace("-", "");
        try { store.locked(context.scope(), () -> {
            store.stageBackground(context.scope(), id, bg);
            store.complete(context.scope(), id, place, keywords, "UPLOAD", overlay, context.simulatedAt()); return null;
        }); } catch (IOException e) { throw storageError(); }
        // Exact place match and newest upload take precedence on the next lookup.
        return resolve(context, place);
    }
    public Path image(Context context, String id, String layer) {
        try {
            if (store.asset(context.scope(), id).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            Path file = store.file(context.scope(), id, layer);
            if (!java.nio.file.Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return file;
        } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
    private Result result(Context context, String status, PocketBackgroundStore.Asset asset, String location, String error, boolean automatic) {
        String base = "/api/worlds/pockets/" + context.sessionId() + "/backgrounds/images/" + asset.id() + "/";
        return new Result(status, new SceneAsset(asset.id(), asset.location(), base + "background", base + "scenery",
                asset.source(), PocketBackgroundImages.WIDTH, PocketBackgroundImages.HEIGHT,
                PocketBackgroundStore.daypart(asset.simulatedAt()), asset.simulatedAt()),
                !PocketBackgroundMatcher.normalize(location).equals(PocketBackgroundMatcher.normalize(asset.location())), error,
                automatic, enabled && generator.available() && !imageBlocked(context));
    }
    private String key(Context context, String location) {
        try { return context.scope() + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("pixel-v2:" + context.daypart() + ":" + PocketBackgroundMatcher.normalize(location)).getBytes(StandardCharsets.UTF_8))).substring(0, 32); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String location(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장소를 200자 이하로 입력해 주세요.");
        return value.trim();
    }
    private ResponseStatusException storageError() { return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "배경 파일을 저장하지 못했어요. 저장 공간과 권한을 확인해 주세요."); }
    @PreDestroy public void close() { worker.shutdownNow(); }
}
