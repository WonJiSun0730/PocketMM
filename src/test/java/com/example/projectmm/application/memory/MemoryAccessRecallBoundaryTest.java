package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemoryAccessRecallBoundaryTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 29, 12, 0);
    private final Character character = new Character("NPC");
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final MemoryAccessStateRepository access = mock(MemoryAccessStateRepository.class);
    private final CharacterStateRepository states = mock(CharacterStateRepository.class);
    private final MemoryCompressionService compression = mock(MemoryCompressionService.class);
    private final MemoryLifecycleService lifecycle = new MemoryLifecycleService(memories);
    private final MemoryRecallService recall = new MemoryRecallService(memories, new MemoryDecayPolicy(),
            compression, states, access, lifecycle, mock(MemoryProvenanceAccessPolicy.class));

    private Memory memory(long id) {
        ReflectionTestUtils.setField(character, "id", 1L);
        var memory = new Memory(character, null, "밀리는 위험한 상황에서 도움을 받았다", 9, 100, 2, now.minusDays(10));
        ReflectionTestUtils.setField(memory, "id", id);
        return memory;
    }

    @Test void thresholdAndRecoveryUseNaturalStrengthWithoutPersistentPenalty() {
        var memory = memory(1L);
        var state = new MemoryAccessState(memory, MemoryAccessLevel.IMPLICIT, now);
        when(memories.findById(1L)).thenReturn(Optional.of(memory));
        when(memories.findAllById(List.of(1L))).thenReturn(List.of(memory));
        when(memories.findByCharacterIdOrderByRememberedAtDesc(1L)).thenReturn(List.of(memory));
        when(access.findByMemoryId(1L)).thenReturn(Optional.of(state));
        when(states.findByCharacterId(1L)).thenReturn(Optional.of(new CharacterState(character, 70, 70, 46, now)));
        assertThat(recall.recall(1L, now, 1)).extracting(EffectiveMemory::effectiveStrength).containsExactly(54);
        state.changeAccessLevel(MemoryAccessLevel.RESIDUAL, now);
        assertThat(recall.recall(1L, now, 1)).isEmpty();
        new MemoryAccessService(memories, access, lifecycle).changeAccess(1L, MemoryAccessLevel.EXPLICIT, now);
        assertThat(recall.recall(1L, now, 1)).extracting(EffectiveMemory::effectiveStrength).containsExactly(90);
        assertThat(memory.getStrength()).isEqualTo(100);
        assertThat(memory.getImportance()).isEqualTo(9);
        assertThat(memory.getNarrativeAnchor()).isEqualTo(2);
        assertThat(memory.getDetailLevel()).isEqualTo(MemoryDetailLevel.DETAILED);
        assertThat(memory.getLastReactivatedAt()).isNull();
        state.changeAccessLevel(MemoryAccessLevel.BLOCKED, now);
        clearInvocations(compression);
        assertThat(recall.recall(1L, now, 1)).isEmpty();
        verifyNoInteractions(compression);
    }

    @Test void conversationTopNDiscountsRelevanceAsWellAsStrength() {
        var explicit = memory(1L);
        var implicit = memory(2L);
        var residual = memory(3L);
        // 제한된 기억의 원문 관련도가 조금 높아도 access 감산을 top-N 전에 반영한다.
        ReflectionTestUtils.setField(explicit, "summary", "abcd");
        ReflectionTestUtils.setField(implicit, "summary", "abcde");
        ReflectionTestUtils.setField(residual, "summary", "abcde");
        when(memories.findForConversation(1L, 10L)).thenReturn(List.of(residual, implicit, explicit));
        when(access.findByMemoryId(2L)).thenReturn(Optional.of(new MemoryAccessState(implicit, MemoryAccessLevel.IMPLICIT, now)));
        when(access.findByMemoryId(3L)).thenReturn(Optional.of(new MemoryAccessState(residual, MemoryAccessLevel.RESIDUAL, now)));
        assertThat(recall.recallForConversation(1L, 10L, now, 2, "abcde"))
                .extracting(EffectiveMemory::memoryId).containsExactly(1L, 2L);
    }

    @Test void restrictedOrOtherConversationEvidenceCannotReachCompression() {
        var current = memory(1L);
        var allowed = memory(2L);
        var blocked = memory(3L);
        var otherRoom = memory(4L);
        ReflectionTestUtils.setField(otherRoom, "conversation", Conversation.fresh(character, now));
        when(memories.findByCharacterIdOrderByRememberedAtDesc(1L)).thenReturn(List.of(current, allowed, blocked, otherRoom));
        var finder = new MemoryPatternCandidateFinder(memories, access, mock(MemoryProvenanceAccessPolicy.class));
        when(access.findMemoryIdsWithDifferentAccess(List.of(2L, 3L), MemoryAccessLevel.EXPLICIT)).thenReturn(List.of(3L));
        assertThat(finder.findRelated(current)).containsExactly(allowed);
        verify(access).findMemoryIdsWithDifferentAccess(List.of(2L, 3L), MemoryAccessLevel.EXPLICIT);
        verify(access, never()).findByMemoryId(anyLong());
    }

    @Test void actualCompressorReceivesOnlyExplicitEvidenceFromSamePersistedConversation() {
        var source = memory(1L);
        var sameRoom = memory(2L);
        var blocked = memory(3L);
        var otherRoom = memory(4L);
        var world = memory(5L);
        var roomA = Conversation.fresh(character, now);
        var roomACopy = Conversation.fresh(character, now);
        var roomB = Conversation.fresh(character, now);
        ReflectionTestUtils.setField(roomA, "id", 10L);
        ReflectionTestUtils.setField(roomACopy, "id", 10L);
        ReflectionTestUtils.setField(roomB, "id", 20L);
        ReflectionTestUtils.setField(source, "conversation", roomA);
        ReflectionTestUtils.setField(sameRoom, "conversation", roomACopy);
        ReflectionTestUtils.setField(blocked, "conversation", roomA);
        ReflectionTestUtils.setField(otherRoom, "conversation", roomB);
        source.compress(source.getSummary());
        when(memories.findByCharacterIdOrderByRememberedAtDesc(1L))
                .thenReturn(List.of(source, sameRoom, blocked, otherRoom, world));
        when(access.findMemoryIdsWithDifferentAccess(List.of(2L, 3L), MemoryAccessLevel.EXPLICIT)).thenReturn(List.of(3L));
        var compressor = mock(MemoryCompressor.class);
        var service = new MemoryCompressionService(new MemoryDecayPolicy(), compressor,
                new MemoryPatternCandidateFinder(memories, access, mock(MemoryProvenanceAccessPolicy.class)), mock(MemoryCompressionPersistenceService.class), access, mock(MemoryProvenanceAccessPolicy.class));
        service.compressIfNeeded(source, now.plusYears(1));
        verify(compressor).compress(source, MemoryDetailLevel.PATTERN, List.of(sameRoom));
        verifyNoMoreInteractions(compressor);
        // 남은 유일한 근거도 제한되면 PATTERN evidence gate에서 호출 자체를 중단한다.
        when(access.findMemoryIdsWithDifferentAccess(List.of(2L, 3L), MemoryAccessLevel.EXPLICIT)).thenReturn(List.of(2L, 3L));
        clearInvocations(compressor);
        service.compressIfNeeded(source, now.plusYears(1));
        verifyNoInteractions(compressor);
    }

    @Test void directCompressionNeverSendsRestrictedSourceToCompressor() {
        var source = memory(1L);
        var compressor = mock(MemoryCompressor.class);
        var finder = mock(MemoryPatternCandidateFinder.class);
        var persistence = mock(MemoryCompressionPersistenceService.class);
        var service = new MemoryCompressionService(new MemoryDecayPolicy(), compressor, finder, persistence, access, mock(MemoryProvenanceAccessPolicy.class));
        for (var level : List.of(MemoryAccessLevel.BLOCKED, MemoryAccessLevel.IMPLICIT, MemoryAccessLevel.RESIDUAL)) {
            when(access.findByMemoryId(1L)).thenReturn(Optional.of(new MemoryAccessState(source, level, now)));
            assertThat(service.compressIfNeeded(source, now.plusYears(1)).compressed()).isFalse();
        }
        verifyNoInteractions(compressor, finder, persistence);
    }

    @Test void explicitNoOpAndLegacyCreationDoNotReactivate() {
        var source = memory(1L);
        when(memories.findById(1L)).thenReturn(Optional.of(source));
        var tracker = mock(MemoryLifecycleService.class);
        var service = new MemoryAccessService(memories, access, tracker);
        service.changeAccess(1L, MemoryAccessLevel.EXPLICIT, now);
        when(access.findByMemoryId(1L)).thenReturn(Optional.of(new MemoryAccessState(source, MemoryAccessLevel.EXPLICIT, now)));
        service.changeAccess(1L, MemoryAccessLevel.EXPLICIT, now);
        verifyNoInteractions(tracker);
    }
}
