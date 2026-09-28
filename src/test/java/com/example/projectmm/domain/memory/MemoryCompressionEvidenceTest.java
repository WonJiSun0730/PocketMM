package com.example.projectmm.domain.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryCompressionEvidenceTest {
    @Test
    void recordsStageAndReferenceWithoutCopyingOrChangingMemory() {
        var current = memory(1L, MemoryDetailLevel.PATTERN);
        var evidence = memory(2L, MemoryDetailLevel.SUMMARY);
        var link = new MemoryCompressionEvidence(current, MemoryDetailLevel.SCHEMA, evidence);
        assertThat(link.getMemory()).isSameAs(current);
        assertThat(link.getEvidenceMemory()).isSameAs(evidence);
        assertThat(link.getTargetLevel()).isEqualTo(MemoryDetailLevel.SCHEMA);
        when(evidence.getDetailLevel()).thenReturn(MemoryDetailLevel.PATTERN);
        assertThat(link.getEvidenceLevel()).isEqualTo(MemoryDetailLevel.SUMMARY);
        verify(current, never()).getSummary();
        verify(evidence, never()).getSummary();
    }

    @Test
    void rejectsSelfReferencesSummaryLinksAndUnpersistedMemories() {
        var current = memory(1L, MemoryDetailLevel.SUMMARY);
        assertThatThrownBy(() -> new MemoryCompressionEvidence(current, MemoryDetailLevel.PATTERN,
                memory(1L, MemoryDetailLevel.DETAILED))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryCompressionEvidence(current, MemoryDetailLevel.SUMMARY,
                memory(2L, MemoryDetailLevel.DETAILED))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemoryCompressionEvidence(current, MemoryDetailLevel.PATTERN,
                memory(null, MemoryDetailLevel.DETAILED))).isInstanceOf(IllegalArgumentException.class);
    }

    private Memory memory(Long id, MemoryDetailLevel level) {
        var memory = mock(Memory.class);
        when(memory.getId()).thenReturn(id);
        when(memory.getDetailLevel()).thenReturn(level);
        return memory;
    }
}
