package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.*;
import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterStateRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemoryRecallFastPathTest {
    final LocalDateTime now=LocalDateTime.of(2026,10,23,12,0);
    final MemoryRepository memories=mock(MemoryRepository.class);
    final MemoryAccessStateRepository access=mock(MemoryAccessStateRepository.class);
    final MemoryDecayPolicy decay=mock(MemoryDecayPolicy.class);
    final MemoryCompressionService compression=mock(MemoryCompressionService.class);
    final MemoryProvenanceAccessPolicy provenance=mock(MemoryProvenanceAccessPolicy.class);
    final MemoryLifecycleService lifecycle=mock(MemoryLifecycleService.class);
    final MemoryTierPolicy tiers=mock(MemoryTierPolicy.class);
    final MemoryRetriever retriever=mock(MemoryRetriever.class);
    final MemoryRecallService service=new MemoryRecallService(memories,decay,compression,
            mock(CharacterStateRepository.class),access,lifecycle,provenance,tiers,retriever);
    Memory memory(long id) {
        var m=mock(Memory.class);var character=mock(Character.class);
        when(character.getId()).thenReturn(7L);when(m.getCharacter()).thenReturn(character);
        when(m.getId()).thenReturn(id);when(m.getRememberedAt()).thenReturn(now.minusHours(1));
        when(m.getSummary()).thenReturn("memory-"+id);
        when(decay.calculate(m,now)).thenReturn(new MemoryDecayResult(100,0,90,false));
        when(tiers.determine(m,now)).thenReturn(MemoryTier.HOT);return m;
    }
    MemoryAccessStateRepository.AccessEntry entry(long id,MemoryAccessLevel level) {
        return new MemoryAccessStateRepository.AccessEntry() {
            public Long getMemoryId(){return id;}
            public MemoryAccessLevel getAccessLevel(){return level;}
        };
    }
    void returnCandidates() {
        when(retriever.retrieveRelevant(anyString(),anyList(),anyInt())).thenAnswer(i->i.getArgument(1));
    }
    @Test void batchesAccessAndPreservesBlockedImplicitInheritedAndFutureBoundaries() {
        var a=memory(1);var b=memory(2);var c=memory(3);var future=memory(4);
        when(future.getRememberedAt()).thenReturn(now.plusSeconds(1));
        when(memories.findForWorldRoomRecall(55L,7L,now)).thenReturn(List.of(a,b,c,future));
        when(access.findAccessEntries(List.of(1L,2L,3L))).thenReturn(List.of(entry(1,MemoryAccessLevel.IMPLICIT),entry(2,MemoryAccessLevel.BLOCKED)));
        when(provenance.restrictions(List.of(1L,2L,3L))).thenReturn(Map.of(3L,MemoryAccessLevel.BLOCKED));returnCandidates();
        var result=service.recallForWorldRoomSession(7L,55L,now,5,"query");
        assertThat(result).extracting(EffectiveMemory::memoryId).containsExactly(1L);
        assertThat(result.getFirst().effectiveStrength()).isEqualTo(54);
        verify(access).findAccessEntries(List.of(1L,2L,3L));verify(access,never()).findByMemoryId(anyLong());
        verify(memories,never()).findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(anyLong());
        verify(decay,never()).calculate(b,now);verify(decay,never()).calculate(c,now);verify(decay,never()).calculate(future,now);
        verify(lifecycle).markAccessed(List.of(1L),now);
    }
    @Test void compressionInvalidatesSnapshotForCurrentAndFollowingCandidates() {
        var a=memory(1);var b=memory(2);
        when(memories.findForWorldRoomRecall(55L,7L,now)).thenReturn(List.of(a,b));
        when(decay.calculate(a,now)).thenReturn(new MemoryDecayResult(100,10,90,true));
        var changed=new boolean[]{false};
        when(compression.compressIfNeeded(a,now)).thenAnswer(i->{changed[0]=true;return null;});
        when(access.findByMemoryId(anyLong())).thenAnswer(i->{
            assertThat(changed[0]).isTrue();return Optional.of(new MemoryAccessState(i.getArgument(0,Long.class)==1L?a:b,MemoryAccessLevel.BLOCKED,now));
        });returnCandidates();
        assertThat(service.recallForWorldRoomSession(7L,55L,now,5,"query")).isEmpty();
        verify(access).findByMemoryId(1L);verify(access).findByMemoryId(2L);
        verify(decay,never()).calculate(b,now);
    }
    @Test void accessChangesAreReadAgainOnTheNextRecall() {
        var a=memory(1);when(memories.findForWorldRoomRecall(55L,7L,now)).thenReturn(List.of(a));
        when(access.findAccessEntries(List.of(1L))).thenReturn(List.of(),List.of(entry(1,MemoryAccessLevel.BLOCKED)));returnCandidates();
        assertThat(service.recallForWorldRoomSession(7L,55L,now,5,"query")).hasSize(1);
        assertThat(service.recallForWorldRoomSession(7L,55L,now,5,"query")).isEmpty();
        verify(access,times(2)).findAccessEntries(List.of(1L));
    }
}
