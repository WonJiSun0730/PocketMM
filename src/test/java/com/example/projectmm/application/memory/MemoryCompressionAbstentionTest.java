package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemoryCompressionAbstentionTest {
    @Test
    void abstentionPreservesSummaryLevelAndIndependentAnchor() {
        var compressor = mock(MemoryCompressor.class);
        var finder = mock(MemoryPatternCandidateFinder.class);
        var service = new MemoryCompressionService(new MemoryDecayPolicy(), compressor, finder,
                mock(MemoryCompressionPersistenceService.class), mock(MemoryAccessStateRepository.class));
        var at = LocalDateTime.of(2026, 1, 1, 12, 0);
        var memory = new Memory(new Character("NPC"), null, "original", 5, 100, 10, at);
        for (String output : new String[]{null, "", "x".repeat(501)}) {
            when(compressor.compress(memory, MemoryDetailLevel.SUMMARY, List.of()))
                    .thenReturn(output == null ? null : new MemoryCompressorOutput(output, List.of()));
            assertThat(service.compressIfNeeded(memory, at.plusYears(1)).compressed()).isFalse();
            assertThat(memory.getSummary()).isEqualTo("original");
            assertThat(memory.getDetailLevel()).isEqualTo(MemoryDetailLevel.DETAILED);
            assertThat(memory.getNarrativeAnchor()).isEqualTo(10);
            assertThat(memory.getImportance()).isEqualTo(5);
        }
    }
}
