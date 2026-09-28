package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDecayResult;
import com.example.projectmm.domain.memory.MemoryRepository;
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

    public List<EffectiveMemory> recall(
            Long characterId,
            LocalDateTime currentTime,
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        List<Memory> memories =
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        );

        return memories.stream()
                // World simulation has no conversation context: never borrow another RP's memories.
                .filter(memory -> memory.getConversation() == null)
                .map(memory ->
                        prepareMemory(
                                memory,
                                currentTime
                        )
                )
                .filter(memory ->
                        memory.effectiveStrength() > 0
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
        if (limit <= 0 || conversationId == null) return List.of();
        return memoryRepository.findForConversation(characterId, conversationId).stream()
                // 독립 시작 방에는 방 소속이 없는 과거 세계 사건 기억도 주입하지 않는다.
                .filter(memory -> includeSharedMemories || memory.getConversation() != null)
                .filter(memory -> !memory.getRememberedAt().isAfter(currentTime))
                .map(memory -> prepareMemory(memory, currentTime))
                .filter(memory -> memory.effectiveStrength() > 0)
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
                decay.compressionRequired(), memory.getEvidenceQuote(), memory.getFact()
        );
    }

    private int relevance(String query, EffectiveMemory memory) {
        int score = MemoryRelevance.overlap(query, memory.summary());
        // 구조화 필드는 원문에 이미 포함된다. 재가산하면 같은 발언도 신형 데이터만 우대하게 된다.
        // 구조 유무와 무관하게 보존된 원문을 검색하고, 동점은 기존 강도·시각 순서를 따른다.
        return Math.max(score, MemoryRelevance.overlap(query, memory.evidenceQuote()));
    }
}
