package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryFact;
import com.example.projectmm.domain.memory.MemoryTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRetrievalRankerTest {

    private final MemoryRetrievalRanker ranker =
            new MemoryRetrievalRanker();

    @Test
    void pocketSelectionDoesNotFillUnusedSlotsWithUnrelatedMemories() {
        var now = LocalDateTime.of(2026, 10, 15, 12, 0);
        var key = memory(1L, "은색 열쇠에는 제비 문양이 있다", 50, now, null);
        var cake = memory(2L, "딸기 케이크를 좋아한다", 99, now, null);
        assertThat(ranker.retrieveRelevant("은색 열쇠 문양을 확인한다", List.of(cake, key), 5))
                .extracting(EffectiveMemory::memoryId).containsExactly(1L);
        assertThat(ranker.retrieveRelevant("마차를 부른다", List.of(cake, key), 5)).isEmpty();
        assertThat(ranker.retrieveRelevant("", List.of(cake, key), 5)).isEmpty();
    }

    @Test
    void ranksLexicallyRelevantMemoryFirst() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
                );

        EffectiveMemory relevant =
                memory(
                        1L,
                        "나는 오리고기를 좋아해",
                        50,
                        now.minusDays(3),
                        null
                );

        EffectiveMemory strongerButIrrelevant =
                memory(
                        2L,
                        "제주도에서 지갑을 잃어버렸다",
                        90,
                        now.minusDays(1),
                        null
                );

        List<EffectiveMemory> result =
                ranker.rank(
                        "오리고기 얘기했었지?",
                        List.of(
                                strongerButIrrelevant,
                                relevant
                        ),
                        2
                );

        assertThat(result)
                .extracting(
                        EffectiveMemory::memoryId
                )
                .containsExactly(
                        1L,
                        2L
                );
    }

    @Test
    void fallsBackToStructuredFactIntent() {

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
                );

        EffectiveMemory preference =
                memory(
                        1L,
                        "unused",
                        50,
                        now.minusDays(3),
                        new MemoryFact(
                                MemoryFact.Kind.PREFERENCE,
                                "나",
                                "좋아해",
                                "오리고기"
                        )
                );

        EffectiveMemory event =
                memory(
                        2L,
                        "unused",
                        90,
                        now.minusDays(1),
                        new MemoryFact(
                                MemoryFact.Kind.REPORTED_EVENT,
                                "나",
                                "잃어버렸어",
                                "지갑"
                        )
                );

        List<EffectiveMemory> result =
                ranker.rank(
                        "내 음식 취향 기억나?",
                        List.of(
                                event,
                                preference
                        ),
                        2
                );

        assertThat(result.get(0).memoryId())
                .isEqualTo(1L);
    }

    private EffectiveMemory memory(
            Long id,
            String summary,
            int strength,
            LocalDateTime rememberedAt,
            MemoryFact fact
    ) {
        return new EffectiveMemory(
                id,
                summary,
                5,
                strength,
                rememberedAt,
                false,
                null,
                fact,
                MemoryAccessLevel.EXPLICIT,
                MemoryTier.WARM
        );
    }

    @Test
    void implementsRetrieverContract() {

        MemoryRetriever retriever =
                new MemoryRetrievalRanker();

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
                );

        EffectiveMemory relevant =
                memory(
                        1L,
                        "나는 오리고기를 좋아해",
                        50,
                        now,
                        null
                );

        EffectiveMemory irrelevant =
                memory(
                        2L,
                        "제주도에서 지갑을 잃어버렸다",
                        90,
                        now,
                        null
                );

        List<EffectiveMemory> result =
                retriever.retrieve(
                        "오리고기 얘기했었지?",
                        List.of(
                                irrelevant,
                                relevant
                        ),
                        1
                );

        assertThat(result)
                .extracting(
                        EffectiveMemory::memoryId
                )
                .containsExactly(1L);
    }
}