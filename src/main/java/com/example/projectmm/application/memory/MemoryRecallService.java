package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.memory.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryRecallService {

    private final MemoryRepository memoryRepository;
    private final MemoryDecayPolicy memoryDecayPolicy;
    private final MemoryCompressionService memoryCompressionService;
    private final CharacterStateRepository characterStateRepository;
    private final MemoryAccessStateRepository memoryAccessStateRepository;
    private final MemoryLifecycleService memoryLifecycleService;
    private final MemoryProvenanceAccessPolicy provenanceAccess;
    private final MemoryTierPolicy memoryTierPolicy;

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
                new MemoryTierPolicy()
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
        List<EffectiveMemory> recalled =
                memories.stream()
                .filter(memory ->
                        accessLevel(memory, restrictions) != MemoryAccessLevel.BLOCKED
                )
                .map(memory -> prepareMemory(memory, currentTime, restrictions))
                .filter(memory ->
                        recallable(
                                memory,
                                recallThreshold
                        )
                )
                .sorted(Comparator.<EffectiveMemory>comparingInt(
                                memory -> relevance(query, memory)).reversed()
                        .thenComparing(Comparator.comparingInt(EffectiveMemory::effectiveStrength).reversed())
                        .thenComparing(EffectiveMemory::rememberedAt, Comparator.reverseOrder()))
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

    private EffectiveMemory prepareMemory(
            Memory memory,
            LocalDateTime currentTime, java.util.Map<Long, MemoryAccessLevel> restrictions
    ) {
        MemoryDecayResult decay =
                memoryDecayPolicy.calculate(
                        memory,
                        currentTime
                );

        if (decay.effectiveStrength() > 0
                && decay.compressionRequired()) {

            memoryCompressionService.compressIfNeeded(
                    memory,
                    currentTime
            );
        }

        MemoryAccessLevel accessLevel =
                accessLevel(memory, restrictions);

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

    private int relevance(
            String query,
            EffectiveMemory memory
    ) {
        int lexicalScore =
                Math.max(
                        MemoryRelevance.overlap(
                                query,
                                memory.summary()
                        ),
                        MemoryRelevance.overlap(
                                query,
                                memory.evidenceQuote()
                        )
                );

        int structuredBonus =
                MemoryRelevance.structuredBonus(
                        query,
                        memory.fact()
                );

        if (lexicalScore > 0) {
            return applyAccessStrength(lexicalScore * 10, memory.accessLevel());
        }

        // 관련도 우선 정렬에서도 접근 감산이 무시되지 않도록 한다. 저장 강도는 변경하지 않는다.
        return applyAccessStrength(structuredBonus, memory.accessLevel());
    }
}
