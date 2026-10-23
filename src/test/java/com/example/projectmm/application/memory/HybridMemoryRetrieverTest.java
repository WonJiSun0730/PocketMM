package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.EffectiveMemory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryTier;
import com.example.projectmm.domain.memory.SemanticMemory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class HybridMemoryRetrieverTest {

    @Test void noSemanticCandidatesKeepRankingWithoutAnyEmbeddingCall() {
        var provider=mock(MemoryEmbeddingProvider.class);
        var documents=mock(CachedMemoryEmbeddingService.class);
        var ranker=new MemoryRetrievalRanker();
        var items=List.of(memory(1L,"potato",50,null),memory(2L,"other",90,null));
        var result=new HybridMemoryRetriever(provider,documents,ranker).retrieve("potato",items,2);
        assertThat(result).isEqualTo(ranker.rank("potato",items,items.size()).stream().limit(2).toList());
        verifyNoInteractions(provider,documents);
    }

    @Test void identicalQueryVectorCanBeSharedWithoutSharingCharacterCandidatesOrResults() {
        var provider=mock(MemoryEmbeddingProvider.class);when(provider.embed("query")).thenReturn(List.of(1.0,0.0));
        var documents=mock(CachedMemoryEmbeddingService.class);
        var semantic=semantic(SemanticMemory.Type.PREFERENCE,"likes");
        when(documents.embeddingFor(1L,semantic)).thenReturn(List.of(1.0,0.0));
        when(documents.embeddingFor(2L,semantic)).thenReturn(List.of(0.5,0.5));
        var retriever=new HybridMemoryRetriever(provider,documents,new MemoryRetrievalRanker());
        MemoryQueryEmbeddingScope.within(()->{
            assertThat(retriever.retrieve("query",List.of(memory(1L,"first",90,semantic)),1)).extracting(EffectiveMemory::memoryId).containsExactly(1L);
            assertThat(retriever.retrieve("query",List.of(memory(2L,"second",90,semantic)),1)).extracting(EffectiveMemory::memoryId).containsExactly(2L);
            return null;
        });
        verify(provider,times(1)).embed("query");verify(documents).embeddingFor(1L,semantic);verify(documents).embeddingFor(2L,semantic);
    }

    @Test
    void semanticSimilarityCanOutrankStrongerMemory() {

        MemoryEmbeddingProvider provider =
                text -> {
                    if (text.contains("PET_NAME")) {
                        return List.of(1.0, 0.0);
                    }

                    if (text.contains("LIKE")) {
                        return List.of(0.0, 1.0);
                    }

                    if (text.contains("강아지")) {
                        return List.of(1.0, 0.0);
                    }

                    return List.of(0.0, 0.0);
                };

        HybridMemoryRetriever retriever =
                retriever(provider);

        EffectiveMemory petMemory =
                memory(
                        1L,
                        "오래된 기억",
                        40,
                        semantic(
                                SemanticMemory.Type.RELATIONSHIP,
                                "PET_NAME"
                        )
                );

        EffectiveMemory preferenceMemory =
                memory(
                        2L,
                        "더 강한 기억",
                        100,
                        semantic(
                                SemanticMemory.Type.PREFERENCE,
                                "LIKE"
                        )
                );

        List<EffectiveMemory> result =
                retriever.retrieve(
                        "내 강아지 이름 기억해?",
                        List.of(
                                preferenceMemory,
                                petMemory
                        ),
                        1
                );

        assertThat(result)
                .extracting(
                        EffectiveMemory::memoryId
                )
                .containsExactly(1L);
    }

    @Test
    void fallsBackWhenQueryEmbeddingIsUnavailable() {

        MemoryEmbeddingProvider provider =
                text -> List.of();

        HybridMemoryRetriever retriever =
                retriever(provider);

        EffectiveMemory relevant =
                memory(
                        1L,
                        "나는 오리고기를 좋아해",
                        40,
                        null
                );

        EffectiveMemory irrelevant =
                memory(
                        2L,
                        "제주도에서 지갑을 잃어버렸다",
                        100,
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

    private HybridMemoryRetriever retriever(
            MemoryEmbeddingProvider provider
    ) {
        return new HybridMemoryRetriever(
                provider,
                new CachedMemoryEmbeddingService(
                        provider,
                        new InMemoryMemoryEmbeddingStore()
                ),
                new MemoryRetrievalRanker()
        );
    }

    private SemanticMemory semantic(
            SemanticMemory.Type type,
            String relation
    ) {
        return new SemanticMemory(
                type,
                "SELF",
                relation,
                "LITERAL:감자",
                SemanticMemory.Polarity.NEUTRAL,
                SemanticMemory.Temporality.PRESENT,
                SemanticMemory.Certainty.CERTAIN
        );
    }

    private EffectiveMemory memory(
            Long id,
            String summary,
            int strength,
            SemanticMemory semanticMemory
    ) {
        return new EffectiveMemory(
                id,
                summary,
                50,
                strength,
                LocalDateTime.of(
                        2026, 9, 29,
                        12, 0
                ),
                false,
                null,
                null,
                semanticMemory,
                MemoryAccessLevel.EXPLICIT,
                MemoryTier.HOT
        );
    }

    @Test
    void keepsLegacyMemoryWithoutSemanticRepresentation() {

        MemoryEmbeddingProvider provider =
                text -> {
                    if (text.contains("LIKE")) {
                        return List.of(1.0, 0.0);
                    }

                    if (text.contains("취향")) {
                        return List.of(1.0, 0.0);
                    }

                    return List.of(0.0, 1.0);
                };

        HybridMemoryRetriever retriever =
                retriever(provider);

        EffectiveMemory semantic =
                memory(
                        1L,
                        "다른 취향에 대한 기억",
                        60,
                        semantic(
                                SemanticMemory.Type.PREFERENCE,
                                "LIKE"
                        )
                );

        EffectiveMemory legacy =
                memory(
                        2L,
                        "나는 오리고기를 좋아해",
                        80,
                        null
                );

        List<EffectiveMemory> result =
                retriever.retrieve(
                        "오리고기 취향",
                        List.of(
                                semantic,
                                legacy
                        ),
                        2
                );

        assertThat(result)
                .extracting(
                        EffectiveMemory::memoryId
                )
                .containsExactlyInAnyOrder(
                        1L,
                        2L
                );
    }
}
