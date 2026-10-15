package com.example.projectmm.application.memory;

import com.example.projectmm.domain.chat.WorldRoomSessionCastRepository;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PocketMemoryInspectorService {

    private final WorldRoomSessionCastRepository casts;
    private final MemoryRepository memories;

    @Transactional(readOnly = true)
    public List<CharacterMemories> latest(Long sessionId) {
        return casts.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(cast -> new CharacterMemories(
                        cast.getCharacter().getId(),
                        cast.getCharacter().getName(),
                        cast.isActive(),
                        memories.findTop5ByWorldRoomSessionIdAndCharacterIdOrderByRememberedAtDescIdDesc(
                                        sessionId,
                                        cast.getCharacter().getId()
                                ).stream()
                                .map(MemoryItem::from)
                                .toList()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CharacterMemories> snapshotAll(Long sessionId) {
        var all = memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(sessionId);
        return casts.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(cast -> new CharacterMemories(
                        cast.getCharacter().getId(),
                        cast.getCharacter().getName(),
                        cast.isActive(),
                        all.stream()
                                .filter(memory -> memory.getCharacter().getId().equals(cast.getCharacter().getId()))
                                .map(MemoryItem::from)
                                .toList()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TurnDiff latestTurnDiff(Long sessionId) {
        var sessionMemories = memories.findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(sessionId);
        if (sessionMemories.isEmpty()) {
            return TurnDiff.empty();
        }
        LocalDateTime turnAt = sessionMemories.getFirst().getRememberedAt();
        var added = memories.findByWorldRoomSessionIdAndRememberedAtOrderByCharacterIdAscIdAsc(sessionId, turnAt)
                .stream()
                .map(memory -> new DiffItem(
                        "ADDED",
                        memory.getId(),
                        memory.getCharacter().getId(),
                        memory.getCharacter().getName(),
                        memory.getSummary()
                ))
                .toList();
        return new TurnDiff(turnAt, added.size(), 0, 0, 0, added);
    }

    public record TurnDiff(
            LocalDateTime turnAt,
            int added,
            int updated,
            int compressed,
            int deleted,
            List<DiffItem> items
    ) {
        static TurnDiff empty() {
            return new TurnDiff(null, 0, 0, 0, 0, List.of());
        }
    }

    public record DiffItem(
            String type,
            Long memoryId,
            Long characterId,
            String characterName,
            String summary
    ) {}

    public record CharacterMemories(
            Long characterId,
            String characterName,
            boolean active,
            List<MemoryItem> memories
    ) {}

    public record MemoryItem(
            Long id,
            String summary,
            String evidenceQuote,
            int importance,
            int strength,
            String detailLevel,
            LocalDateTime rememberedAt,
            LocalDateTime lastAccessedAt,
            LocalDateTime lastReactivatedAt
    ) {
        static MemoryItem from(Memory memory) {
            return new MemoryItem(
                    memory.getId(),
                    memory.getSummary(),
                    memory.getEvidenceQuote(),
                    memory.getImportance(),
                    memory.getStrength(),
                    memory.getDetailLevel().name(),
                    memory.getRememberedAt(),
                    memory.getLastAccessedAt(),
                    memory.getLastReactivatedAt()
            );
        }
    }
}
