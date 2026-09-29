package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailSalience;
import com.example.projectmm.domain.memory.ProtectedMemoryDetail;
import com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtectedMemoryDetailPolicyTest {

    private final ProtectedMemoryDetailRepository repository =
            mock(ProtectedMemoryDetailRepository.class);

    private final ProtectedMemoryDetailPolicy policy =
            new ProtectedMemoryDetailPolicy(repository);

    @Test
    void HIGH_디테일은_자동으로_보호된다() {

        Memory memory =
                mock(Memory.class);

        ProtectedMemoryDetail detail =
                new ProtectedMemoryDetail(
                        memory,
                        "왼손에 반지를 끼워줬다",
                        MemoryDetailSalience.HIGH,
                        false
                );

        assertThat(detail.isProtectedAnchor())
                .isTrue();

        assertThat(detail.mustPreserve())
                .isTrue();
    }

    @Test
    void NORMAL이어도_명시적_anchor면_보호된다() {

        Memory memory =
                mock(Memory.class);

        ProtectedMemoryDetail detail =
                new ProtectedMemoryDetail(
                        memory,
                        "비가 내리고 있었다",
                        MemoryDetailSalience.NORMAL,
                        true
                );

        assertThat(detail.isProtectedAnchor())
                .isTrue();

        assertThat(detail.mustPreserve())
                .isTrue();
    }

    @Test
    void LOW이고_anchor가_아니면_보호하지_않는다() {

        Memory memory =
                mock(Memory.class);

        ProtectedMemoryDetail detail =
                new ProtectedMemoryDetail(
                        memory,
                        "커피는 라테였다",
                        MemoryDetailSalience.LOW,
                        false
                );

        assertThat(detail.isProtectedAnchor())
                .isFalse();

        assertThat(detail.mustPreserve())
                .isFalse();
    }

    @Test
    void 보호된_디테일이_summary에_모두_남아있으면_압축을_허용한다() {

        Memory memory =
                mock(Memory.class);

        ProtectedMemoryDetail first =
                new ProtectedMemoryDetail(
                        memory,
                        "왼손에 반지를 끼워줬다",
                        MemoryDetailSalience.HIGH,
                        false
                );

        ProtectedMemoryDetail second =
                new ProtectedMemoryDetail(
                        memory,
                        "다시는 떠나지 않겠다고 약속했다",
                        MemoryDetailSalience.NORMAL,
                        true
                );

        when(
                repository.findByMemoryIdOrderByIdAsc(1L)
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        boolean preserved =
                policy.preservesProtectedDetails(
                        1L,
                        "그는 왼손에 반지를 끼워줬다. "
                                + "그리고 다시는 떠나지 않겠다고 약속했다."
                );

        assertThat(preserved)
                .isTrue();
    }

    @Test
    void 보호된_디테일_하나라도_summary에서_사라지면_압축을_거부한다() {

        Memory memory =
                mock(Memory.class);

        ProtectedMemoryDetail first =
                new ProtectedMemoryDetail(
                        memory,
                        "왼손에 반지를 끼워줬다",
                        MemoryDetailSalience.CRITICAL,
                        false
                );

        ProtectedMemoryDetail second =
                new ProtectedMemoryDetail(
                        memory,
                        "다시는 떠나지 않겠다고 약속했다",
                        MemoryDetailSalience.HIGH,
                        false
                );

        when(
                repository.findByMemoryIdOrderByIdAsc(1L)
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        boolean preserved =
                policy.preservesProtectedDetails(
                        1L,
                        "그는 왼손에 반지를 끼워줬다."
                );

        assertThat(preserved)
                .isFalse();
    }

    @Test
    void 보호할_디테일이_없으면_압축을_허용한다() {

        when(
                repository.findByMemoryIdOrderByIdAsc(1L)
        ).thenReturn(List.of());

        assertThat(
                policy.preservesProtectedDetails(
                        1L,
                        "자유롭게 압축된 요약"
                )
        ).isTrue();
    }
}