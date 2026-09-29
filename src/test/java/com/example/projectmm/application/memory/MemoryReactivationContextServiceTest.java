package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryReactivationContextServiceTest {

    private final MemoryCompressionEvidenceRepository evidenceRepository =
            mock(MemoryCompressionEvidenceRepository.class);

    private final MemoryAccessStateRepository accessStateRepository =
            mock(MemoryAccessStateRepository.class);

    private final MemoryProvenanceAccessPolicy provenanceAccess =
            mock(MemoryProvenanceAccessPolicy.class);

    private final MemoryRepository memoryRepository =
            mock(MemoryRepository.class);

    private final MemoryReactivationContextService service =
            new MemoryReactivationContextService(
                    evidenceRepository,
                    accessStateRepository,
                    provenanceAccess,
                    memoryRepository
            );

    @Test
    void 상세_기억은_회상_확장_대상이_아니다() {

        Memory memory =
                new Memory(
                        null,
                        null,
                        "상세 기억",
                        8,
                        80,
                        LocalDateTime.of(
                                2026, 9, 29,
                                12, 0
                        )
                );

        assertThat(
                service.accessibleEvidence(memory)
        ).isEmpty();
    }

    @Test
    void 접근_가능한_압축_근거만_회상_확장에_사용한다() {

        Memory source =
                mock(Memory.class);

        Memory explicitEvidence =
                mock(Memory.class);

        Memory blockedEvidence =
                mock(Memory.class);

        MemoryCompressionEvidence explicitEdge =
                mock(MemoryCompressionEvidence.class);

        MemoryCompressionEvidence blockedEdge =
                mock(MemoryCompressionEvidence.class);

        when(source.getId())
                .thenReturn(1L);

        when(source.getDetailLevel())
                .thenReturn(
                        com.example.projectmm.domain.memory.MemoryDetailLevel.PATTERN
                );

        when(explicitEvidence.getId())
                .thenReturn(2L);

        when(blockedEvidence.getId())
                .thenReturn(3L);

        when(explicitEdge.getEvidenceMemory())
                .thenReturn(explicitEvidence);

        when(blockedEdge.getEvidenceMemory())
                .thenReturn(blockedEvidence);

        when(
                evidenceRepository.findForReactivation(1L)
        ).thenReturn(
                List.of(
                        explicitEdge,
                        blockedEdge
                )
        );

        when(
                provenanceAccess.restrictions(
                        List.of(2L, 3L)
                )
        ).thenReturn(
                Map.of(
                        2L,
                        MemoryAccessLevel.EXPLICIT,
                        3L,
                        MemoryAccessLevel.BLOCKED
                )
        );

        assertThat(
                service.accessibleEvidence(source)
        )
                .containsExactly(
                        explicitEvidence
                );
    }

    @Test
    void provenance가_IMPLICIT이면_회상_확장에_사용하지_않는다() {

        Memory source =
                mock(Memory.class);

        Memory evidence =
                mock(Memory.class);

        MemoryCompressionEvidence edge =
                mock(MemoryCompressionEvidence.class);

        when(source.getId())
                .thenReturn(1L);

        when(source.getDetailLevel())
                .thenReturn(
                        com.example.projectmm.domain.memory.MemoryDetailLevel.SCHEMA
                );

        when(evidence.getId())
                .thenReturn(2L);

        when(edge.getEvidenceMemory())
                .thenReturn(evidence);

        when(
                evidenceRepository.findForReactivation(1L)
        ).thenReturn(
                List.of(edge)
        );

        when(
                provenanceAccess.restrictions(
                        List.of(2L)
                )
        ).thenReturn(
                Map.of(
                        2L,
                        MemoryAccessLevel.IMPLICIT
                )
        );

        assertThat(
                service.accessibleEvidence(source)
        ).isEmpty();
    }
}