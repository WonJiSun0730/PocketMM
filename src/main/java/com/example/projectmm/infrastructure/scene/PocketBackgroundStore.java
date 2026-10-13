package com.example.projectmm.infrastructure.scene;

import com.example.projectmm.application.chat.PocketBackgroundMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Component
public class PocketBackgroundStore {
    public record Asset(String id, String location, String keywords, String source, String createdAt) {}
    public record Catalog(boolean automatic, List<Asset> assets) {}
    public record Settings(boolean automatic) {}
    private final Path root;
    private final ObjectMapper mapper;
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.locks.ReentrantLock> locks = new java.util.concurrent.ConcurrentHashMap<>();
    public PocketBackgroundStore(@Value("${pocket.background.directory:./data/pocket-backgrounds}") String directory,
                                 ObjectMapper mapper) {
        root = Path.of(directory).toAbsolutePath().normalize(); this.mapper = mapper;
    }
    private Path directory(String scope) {
        if (!scope.matches("[0-9]+-[0-9]+")) throw new IllegalArgumentException("Invalid scope");
        return root.resolve(scope);
    }
    public Path file(String scope, String id, String layer) {
        if (!id.matches("[a-f0-9]{32}") || !Set.of("background", "scenery").contains(layer))
            throw new IllegalArgumentException("Invalid image identifier");
        return directory(scope).resolve(id + "-" + layer + ".png");
    }
    public Catalog catalog(String scope) {
        Path path = directory(scope).resolve("index.json");
        Path settings = directory(scope).resolve("settings.json");
        try {
            Catalog catalog = Files.exists(path) ? mapper.readValue(Files.readAllBytes(path), Catalog.class) : new Catalog(false, List.of());
            boolean automatic = Files.exists(settings) ? mapper.readValue(Files.readAllBytes(settings), Settings.class).automatic() : catalog.automatic();
            return new Catalog(automatic, catalog.assets());
        }
        catch (IOException | RuntimeException e) { throw new IllegalStateException("배경 목록을 읽지 못했어요. 저장 상태를 확인해 주세요.", e); }
    }
    public Optional<Asset> match(String scope, String location) {
        return catalog(scope).assets().stream().filter(a -> Files.isRegularFile(file(scope, a.id(), "background"))
                        && Files.isRegularFile(file(scope, a.id(), "scenery")))
                .filter(a -> PocketBackgroundMatcher.score(location, a.location(), a.keywords()) > 0)
                .max(Comparator.<Asset>comparingInt(a -> PocketBackgroundMatcher.score(location, a.location(), a.keywords()))
                        .thenComparing(a -> a.source().equals("UPLOAD") ? 1 : 0).thenComparing(Asset::createdAt));
    }
    public Optional<Asset> asset(String scope, String id) {
        return catalog(scope).assets().stream().filter(a -> a.id().equals(id)).findFirst();
    }
    public byte[] stagedBackground(String scope, String id) throws IOException {
        Path p = file(scope, id, "background"); return Files.exists(p) ? Files.readAllBytes(p) : null;
    }
    public void stageBackground(String scope, String id, byte[] bytes) throws IOException { atomic(file(scope, id, "background"), bytes); }
    public void complete(String scope, String id, String location, String keywords, String source, byte[] scenery) throws IOException {
        atomic(file(scope, id, "scenery"), scenery);
        Catalog old = catalog(scope); var assets = new ArrayList<>(old.assets());
        assets.removeIf(a -> a.id().equals(id));
        assets.add(new Asset(id, location, keywords == null ? "" : keywords, source, Instant.now().toString()));
        atomic(directory(scope).resolve("index.json"), mapper.writeValueAsBytes(new Catalog(old.automatic(), assets)));
    }
    public void configure(String scope, boolean automatic) throws IOException {
        // A toggle must not wait for a multi-minute image call holding the catalog lock.
        atomic(directory(scope).resolve("settings.json"), mapper.writeValueAsBytes(new Settings(automatic)));
    }
    public <T> T locked(String scope, IOAction<T> action) throws IOException {
        var local = locks.computeIfAbsent(scope, ignored -> new java.util.concurrent.locks.ReentrantLock());
        local.lock();
        try {
            Files.createDirectories(directory(scope));
            try (var channel = FileChannel.open(directory(scope).resolve("catalog.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) { return action.run(); }
        } finally { local.unlock(); }
    }
    @FunctionalInterface public interface IOAction<T> { T run() throws IOException; }
    private void atomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "pending-", ".tmp");
        try { Files.write(temp, bytes);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
