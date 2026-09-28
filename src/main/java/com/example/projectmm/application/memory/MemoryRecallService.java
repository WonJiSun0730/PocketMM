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
                        );

        return memories.stream()
                // World simulation has no conversation context: never borrow another RP's memories.
                .filter(memory -> memory.getConversation() == null)
                .filter(memory ->
                        accessible(memory)
                )
                .map(memory ->
                        prepareMemory(
                                memory,
                                currentTime
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
        return memoryRepository.findForConversation(characterId, conversationId).stream()
                // 독립 시작 방에는 방 소속이 없는 과거 세계 사건 기억도 주입하지 않는다.
                .filter(memory -> includeSharedMemories || memory.getConversation() != null)
                .filter(memory -> !memory.getRememberedAt().isAfter(currentTime))
                .filter(memory ->
                        accessible(memory)
                )
                .map(memory -> prepareMemory(memory, currentTime))
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
    }

    private EffectiveMemory prepareMemory(
            Memory memory,
            LocalDateTime currentTime
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

        return new EffectiveMemory(
                memory.getId(),
                memory.getSummary(),
                memory.getImportance(),
                decay.effectiveStrength(),
                memory.getRememberedAt(),
                decay.compressionRequired(),
                memory.getEvidenceQuote(),
                memory.getFact(),
                accessLevel(memory)
        );
    }

    private boolean accessible(
            Memory memory
    ) {
        return accessLevel(memory)
                != MemoryAccessLevel.BLOCKED;
    }

    private MemoryAccessLevel accessLevel(
            Memory memory
    ) {
        if (memory.getId() == null) {
            return MemoryAccessLevel.EXPLICIT;
        }

        return memoryAccessStateRepository
                .findByMemoryId(
                        memory.getId()
                )
                .map(state ->
                        state.getAccessLevel()
                )
                .orElse(
                        MemoryAccessLevel.EXPLICIT
                );
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
            return lexicalScore * 10;
        }

        return structuredBonus;
    }
}
