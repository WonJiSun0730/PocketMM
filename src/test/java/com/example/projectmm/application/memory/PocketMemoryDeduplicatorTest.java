package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PocketMemoryDeduplicatorTest {
    private EffectiveMemory memory(long id, String text) {
        return new EffectiveMemory(id,text,7,70,LocalDateTime.of(2026,10,15,12,0).plusMinutes(id),false,text,null);
    }
    @Test void exactDuplicatesChooseRecentWithoutChangingOriginalList() {
        var source=List.of(memory(1,"교회 북문에서 만난다."),memory(2,"교회 북문에서 만난다."));
        assertThat(PocketMemoryDeduplicator.select(source,5)).extracting(EffectiveMemory::memoryId).containsExactly(2L);
        assertThat(source).hasSize(2);
        assertThat(source.getFirst().rememberedAt()).isEqualTo(LocalDateTime.of(2026,10,15,12,1));
    }
    @Test void completeSentencesPreferDetailEvenWhenOlderAndRefillFreedSlot() {
        var longMemory=memory(1,"은색 열쇠를 발견했다. 교회 북문에서 밤 열 시에 만난다.");
        var shortMemory=memory(2,"교회 북문에서 밤 열 시에 만난다.");
        var distinct=memory(3,"배달원은 회색 코트를 입었다.");
        assertThat(PocketMemoryDeduplicator.select(List.of(shortMemory,longMemory,distinct),2))
                .extracting(EffectiveMemory::memoryId).containsExactly(1L,3L);
    }
    @Test void differentPlansAndCorrectionsAndFragmentsAreNotMerged() {
        var source=List.of(memory(1,"교회 북문에서 만난다."),memory(2,"교회 남문에서 만난다."),
                memory(3,"교회 북문에서 만난다. 그 약속은 취소했다."),memory(4,"교회 북문"));
        assertThat(PocketMemoryDeduplicator.select(source,5)).hasSize(4);
    }
    @Test void laterSupersetMergesMultipleOverlappingGroups() {
        assertThat(PocketMemoryDeduplicator.select(List.of(memory(1,"첫 단서다."),memory(2,"둘째 단서다."),
                memory(3,"첫 단서다. 둘째 단서다.")),5)).extracting(EffectiveMemory::memoryId).containsExactly(3L);
    }
    @Test void accessLevelsAndDifferentStructuredFactsAreNotMerged() {
        var base=memory(1,"열쇠를 보았다.");
        var restricted=new EffectiveMemory(2L,base.summary(),7,70,base.rememberedAt(),false,base.evidenceQuote(),null,MemoryAccessLevel.RESIDUAL);
        var fact=new EffectiveMemory(3L,base.summary(),7,70,base.rememberedAt(),false,base.evidenceQuote(),new MemoryFact(MemoryFact.Kind.REPORTED_EVENT,"나","보았다","열쇠"));
        assertThat(PocketMemoryDeduplicator.select(List.of(base,restricted,fact),5)).hasSize(3);
    }
}
