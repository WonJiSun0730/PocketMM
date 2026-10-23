package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.memory.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class MemoryRecallService {

    private final MemoryRepository memoryRepository;
    private final MemoryDecayPolicy memoryDecayPolicy;
    private final MemoryCompressionService memoryCompressionService;
    private final CharacterStateRepository characterStateRepository;
    private final MemoryAccessStateRepository memoryAccessStateRepository;
    private final MemoryLifecycleService memoryLifecycleService;
    private final MemoryProvenanceAccessPolicy provenanceAccess;
    private final MemoryTierPolicy memoryTierPolicy;
    private final MemoryRetriever memoryRetriever;

    @Autowired
    public MemoryRecallService(
            MemoryRepository memoryRepository,
            MemoryDecayPolicy memoryDecayPolicy,
            MemoryCompressionService memoryCompressionService,
            CharacterStateRepository characterStateRepository,
            MemoryAccessStateRepository memoryAccessStateRepository,
            MemoryLifecycleService memoryLifecycleService,
            MemoryProvenanceAccessPolicy provenanceAccess,
            MemoryTierPolicy memoryTierPolicy,
            MemoryRetriever memoryRetriever
    ) {
        this.memoryRepository = memoryRepository;
        this.memoryDecayPolicy = memoryDecayPolicy;
        this.memoryCompressionService = memoryCompressionService;
        this.characterStateRepository = characterStateRepository;
        this.memoryAccessStateRepository = memoryAccessStateRepository;
        this.memoryLifecycleService = memoryLifecycleService;
        this.provenanceAccess = provenanceAccess;
        this.memoryTierPolicy = memoryTierPolicy;
        this.memoryRetriever = memoryRetriever;
    }

    public MemoryRecallService(
            MemoryRepository memoryRepository,
            MemoryDecayPolicy memoryDecayPolicy,
            MemoryCompressionService memoryCompressionService,
            CharacterStateRepository characterStateRepository,
            MemoryAccessStateRepository memoryAccessStateRepository,
            MemoryLifecycleService memoryLifecycleService,
            MemoryProvenanceAccessPolicy provenanceAccess
    ) {
        this(
                memoryRepository,
                memoryDecayPolicy,
                memoryCompressionService,
                characterStateRepository,
                memoryAccessStateRepository,
                memoryLifecycleService,
                provenanceAccess,
                new MemoryTierPolicy(),
                new MemoryRetrievalRanker()
        );
    }

    public List<EffectiveMemory> recall(
            Long characterId,
            LocalDateTime currentTime,
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        int recallThreshold =
                recallThreshold(characterId);

        List<Memory> memories =
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        ).stream().filter(memory -> memory.getConversation() == null).toList();

        var restrictions = provenanceAccess.restrictions(memories.stream().map(Memory::getId).toList());
        List<EffectiveMemory> recalled =
                memories.stream()
                .filter(memory ->
                        accessLevel(memory, restrictions) != MemoryAccessLevel.BLOCKED
                )
                .map(memory ->
                        prepareMemory(
                                memory,
                                currentTime, restrictions
                        )
                )
                .filter(memory ->
                        recallable(
                                memory,
                                recallThreshold
                        )
                )
                .sorted(
                        Comparator.comparingInt(
                                EffectiveMemory::effectiveStrength
                        ).reversed()
                )
                .limit(limit)
                .toList();

        memoryLifecycleService.markAccessed(
                recalled.stream()
                        .map(EffectiveMemory::memoryId)
                        .toList(),
                currentTime
        );

        return recalled;
    }

    public List<EffectiveMemory> recallForConversation(Long characterId, Long conversationId,
                                                       LocalDateTime currentTime, int limit, String query) {
        return recallForConversation(characterId, conversationId, currentTime, limit, query, true);
    }

    public List<EffectiveMemory> recallForConversation(Long characterId, Long conversationId,
                                                       LocalDateTime currentTime, int limit, String query,
                                                       boolean includeSharedMemories) {
        if (limit <= 0 || conversationId == null) {
            return List.of();
        }

        int recallThreshold =
                recallThreshold(characterId);
        // provenance 조회도 이번 방에서 실제 검토할 후보에 한정한다.
        var memories = memoryRepository.findForConversation(characterId, conversationId).stream()
                .filter(memory -> includeSharedMemories || memory.getConversation() != null)
                .filter(memory -> !memory.getRememberedAt().isAfter(currentTime)).toList();
        var restrictions = provenanceAccess.restrictions(memories.stream().map(Memory::getId).toList());
        List<EffectiveMemory> candidates =
                memories.stream()
                        .filter(memory ->
                                accessLevel(
                                        memory,
                                        restrictions
                                ) != MemoryAccessLevel.BLOCKED
                        )
                        .map(memory ->
                                prepareMemory(
                                        memory,
                                        currentTime,
                                        restrictions
                                )
                        )
                        .filter(memory ->
                                recallable(
                                        memory,
                                        recallThreshold
                                )
                        )
                        .toList();

        List<EffectiveMemory> recalled =
                memoryRetriever.retrieve(
                        query,
                        candidates,
                        limit
                );

        memoryLifecycleService.markAccessed(
                recalled.stream()
                        .map(EffectiveMemory::memoryId)
                        .toList(),
                currentTime
        );

        return recalled;
    }

    public List<EffectiveMemory> recallForWorldRoomSession(
            Long characterId,
            Long sessionId,
            LocalDateTime currentTime,
            int limit,
            String query
    ) {
        if (limit <= 0 || sessionId == null) {
            return List.of();
        }

        int recallThreshold = recallThreshold(characterId);
        var memories = memoryRepository
                .findForWorldRoomRecall(sessionId, characterId, currentTime)
                .stream()
                .filter(memory -> characterId.equals(memory.getCharacter().getId()))
                .filter(memory -> !memory.getRememberedAt().isAfter(currentTime))
                .toList();

        var restrictions = provenanceAccess.restrictions(
                memories.stream().map(Memory::getId).toList()
        );

        var ownAccess = new java.util.HashMap<Long, MemoryAccessLevel>();
        var ids = memories.stream().map(Memory::getId).filter(java.util.Objects::nonNull).distinct().toList();
        for (int offset = 0; offset < ids.size(); offset += 256) {
            memoryAccessStateRepository.findAccessEntries(ids.subList(offset, Math.min(offset + 256, ids.size())))
                    .forEach(entry -> ownAccess.put(entry.getMemoryId(), entry.getAccessLevel()));
        }
        // Compression commits separately and can change access. Fall back to fresh reads afterwards.
        boolean[] compressionAttempted = {false};
        java.util.function.Function<Memory, MemoryAccessLevel> lookup = memory -> compressionAttempted[0]
                ? accessLevel(memory, restrictions)
                : MemoryProvenanceAccessPolicy.stricter(ownAccess.getOrDefault(memory.getId(), MemoryAccessLevel.EXPLICIT),
                        restrictions.getOrDefault(memory.getId(), MemoryAccessLevel.EXPLICIT));
        List<EffectiveMemory> candidates = memories.stream()
                .filter(memory -> lookup.apply(memory) != MemoryAccessLevel.BLOCKED)
                .map(memory -> prepareMemory(memory, currentTime, lookup, () -> compressionAttempted[0] = true))
                .filter(memory -> recallable(memory, recallThreshold))
                .toList();

        List<EffectiveMemory> recalled = memoryRetriever.retrieveRelevant(
                query,
                candidates,
                limit
        );

        memoryLifecycleService.markAccessed(
                recalled.stream().map(EffectiveMemory::memoryId).toList(),
                currentTime
        );

        return recalled;
    }

    private EffectiveMemory prepareMemory(
            Memory memory,
            LocalDateTime currentTime, java.util.Map<Long, MemoryAccessLevel> restrictions
    ) {
        return prepareMemory(memory, currentTime, m -> accessLevel(m, restrictions), () -> {});
    }

    private EffectiveMemory prepareMemory(Memory memory, LocalDateTime currentTime,
            java.util.function.Function<Memory, MemoryAccessLevel> lookup, Runnable invalidate) {
        MemoryDecayResult decay =
                memoryDecayPolicy.calculate(
                        memory,
                        currentTime
                );

        if (decay.effectiveStrength() > 0
                && decay.compressionRequired()) {

            invalidate.run();
            memoryCompressionService.compressIfNeeded(
                    memory,
                    currentTime
            );
        }

        MemoryAccessLevel accessLevel = lookup.apply(memory);

        int accessibleStrength =
                applyAccessStrength(
                        decay.effectiveStrength(),
                        accessLevel
                );

        MemoryTier tier =
                memoryTierPolicy.determine(
                        memory,
                        currentTime
                );

        return new EffectiveMemory(
                memory.getId(),
                memory.getSummary(),
                memory.getImportance(),
                accessibleStrength,
                memory.getRememberedAt(),
                decay.compressionRequired(),
                memory.getEvidenceQuote(),
                memory.getFact(),
                memory.getSemanticMemory(),
                accessLevel,
                tier
        );
    }

    private int applyAccessStrength(
            int effectiveStrength,
            MemoryAccessLevel accessLevel
    ) {
        return switch (accessLevel) {
            case EXPLICIT ->
                    effectiveStrength;

            case IMPLICIT ->
                    effectiveStrength * 60 / 100;

            case RESIDUAL ->
                    effectiveStrength * 30 / 100;

            case BLOCKED ->
                    0;
        };
    }

    private MemoryAccessLevel accessLevel(Memory memory, java.util.Map<Long, MemoryAccessLevel> restrictions) {
        if (memory.getId() == null) return MemoryAccessLevel.EXPLICIT;
        var own = memoryAccessStateRepository.findByMemoryId(memory.getId())
                .map(state -> state.getAccessLevel()).orElse(MemoryAccessLevel.EXPLICIT);
        return MemoryProvenanceAccessPolicy.stricter(own,
                restrictions.getOrDefault(memory.getId(), MemoryAccessLevel.EXPLICIT));
    }
    private int recallThreshold(
            Long characterId
    ) {
        int memoryAbility =
                characterStateRepository
                        .findByCharacterId(characterId)
                        .map(state ->
                                state.getMemoryAbility()
                        )
                        .orElse(100);

        return 100 - memoryAbility;
    }

    private boolean recallable(
            EffectiveMemory memory,
            int threshold
    ) {
        return memory.effectiveStrength() > 0
                && memory.effectiveStrength() >= threshold;
    }

}
