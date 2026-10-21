package com.example.projectmm.application.world;

import com.example.projectmm.domain.world.*;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.member.MemberRepository;
import com.example.projectmm.application.chat.WorldRoomSessionService;
import com.example.projectmm.api.world.WorldRoomSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import java.util.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WorldPublicationService {
    private final WorldRepository worlds;
    private final MemberRepository members;
    private final WorldRoomIntroRepository intros;
    private final WorldRoomMessageRepository messages;
    private final WorldRoomSessionService sessions;

    public record Card(Long id, String name, String coverImageUrl, List<String> hashtags,
                       long totalTurns, String publisher, LocalDateTime publishedAt) {}
    public record Intro(Long id, Long roomId, String roomTitle, String title, String content) {}
    public record Detail(Card world, String playGuide, List<Intro> intros) {}
    public record Listing(List<Card> items, int page, boolean hasNext) {}
    public record Settings(String coverImageUrl, List<String> hashtags, LocalDateTime publishedAt) {}
    public record Started(Long worldId, String worldName, Long roomId, String roomTitle, WorldRoomSessionResponse session) {}

    @Transactional(readOnly = true)
    public Listing list(int page) {
        if (page < 0 || page > 10000) throw bad("페이지 범위를 확인해 주세요.");
        var found = worlds.findByPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(PageRequest.of(page, 24));
        var counts = counts(found.getContent());
        return new Listing(found.getContent().stream().map(w -> card(w, counts.getOrDefault(w.getId(), 0L))).toList(), page, found.hasNext());
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        var world = published(id);
        return new Detail(card(world, counts(List.of(world)).getOrDefault(id, 0L)), world.getPlayGuide(),
                intros.findByRoomWorldIdOrderByRoomIdAscSortOrderAscIdAsc(id).stream()
                        .map(i -> new Intro(i.getId(), i.getRoom().getId(), i.getRoom().getTitle(), i.getTitle(), i.getContent())).toList());
    }

    @Transactional(readOnly = true)
    public Settings settings(Long id, String login) { return settings(owned(id, login)); }

    @Transactional
    public Settings update(Long id, String login, List<String> tags) {
        var world = owned(id, login);
        var normalized = new LinkedHashSet<String>();
        if (tags != null) {
            if (tags.size() > 10) throw bad("해시태그는 최대 10개예요.");
            for (var raw : tags) {
                if (raw == null) throw bad("해시태그를 확인해 주세요.");
                var tag = raw.trim().replaceFirst("^#+", "");
                if (tag.isBlank()) continue;
                if (tag.codePointCount(0, tag.length()) > 30 || !tag.matches("[\\p{L}\\p{N}_-]+")) throw bad("해시태그는 글자·숫자·밑줄·하이픈으로 30자까지 입력할 수 있어요.");
                normalized.add(tag);
            }
        }
        world.updateHashtags(String.join("\n", normalized));
        return settings(world);
    }

    @Transactional
    public Settings publish(Long id, String login) {
        var world = owned(id, login);
        if (world.getCoverImageUrl() == null || world.getCoverImageUrl().isBlank()) throw bad("배포 전에 월드 표지를 등록해 주세요.");
        if (intros.findByRoomWorldIdOrderByRoomIdAscSortOrderAscIdAsc(id).isEmpty()) throw bad("배포 전에 인트로를 하나 이상 만들어 주세요.");
        world.publish();
        return settings(world);
    }

    @Transactional
    public Settings unpublish(Long id, String login) {
        var world = owned(id, login);
        world.unpublish();
        return settings(world);
    }

    @Transactional
    public Started start(Long id, String login, Long introId, Long profileId) {
        var world = published(id);
        var intro = intros.findById(introId).filter(i -> i.getRoom().getWorld().getId().equals(id))
                .orElseThrow(() -> missing());
        var session = sessions.startPublished(id, intro.getRoom().getId(), login, profileId, introId);
        return new Started(id, world.getName(), intro.getRoom().getId(), intro.getRoom().getTitle(), WorldRoomSessionResponse.from(session));
    }

    public World owned(Long id, String login) {
        var member = members.findByLoginId(login).orElseThrow(() -> missing());
        return worlds.findByIdAndOwnerId(id, member.getId()).orElseThrow(() -> missing());
    }
    public World published(Long id) {
        return worlds.findById(id).filter(w -> w.getPublishedAt() != null).orElseThrow(() -> missing());
    }
    private Settings settings(World w) { return new Settings(w.getCoverImageUrl(), tags(w), w.getPublishedAt()); }
    private List<String> tags(World w) { return w.getHashtags() == null || w.getHashtags().isBlank() ? List.of() : List.of(w.getHashtags().split("\n")); }
    private Card card(World w, long count) { return new Card(w.getId(), w.getName(), w.getCoverImageUrl(), tags(w), count, w.getOwner().getName(), w.getPublishedAt()); }
    private Map<Long, Long> counts(List<World> found) {
        var result = new HashMap<Long, Long>();
        if (!found.isEmpty()) messages.countTurnsByWorldIds(found.stream().map(World::getId).toList()).forEach(c -> result.put(c.getWorldId(), c.getTurns()));
        return result;
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "월드를 찾을 수 없어요."); }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
}
