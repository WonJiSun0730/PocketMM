package com.example.projectmm.application.memory;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailSalience;
import com.example.projectmm.domain.memory.ProtectedMemoryDetail;
import com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryDetailServiceTest {

    private final ProtectedMemoryDetailRepository details =
            mock(ProtectedMemoryDetailRepository.class);

    private final MemoryDetailService service =
            new MemoryDetailService(details);

    @Test
    void highSalienceIsProtectedAndSaved() {

        Memory memory = memory();

        when(details.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ProtectedMemoryDetail> saved =
                service.saveDetails(
                        memory,
                        List.of(
                                new MemoryDetailCandidate(
                                        "왼손에 반지를 끼워줬다",
                                        MemoryDetailSalience.HIGH,
                                        false
                                )
                        )
                );

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getDetailText())
                .isEqualTo("왼손에 반지를 끼워줬다");
        assertThat(saved.getFirst().mustPreserve()).isTrue();

        verify(details, times(1))
                .save(any(ProtectedMemoryDetail.class));
    }

    @Test
    void normalSalienceWithExplicitAnchorIsSaved() {

        Memory memory = memory();

        when(details.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ProtectedMemoryDetail> saved =
                service.saveDetails(
                        memory,
                        List.of(
                                new MemoryDetailCandidate(
                                        "다시는 떠나지 않겠다고 약속했다",
                                        MemoryDetailSalience.NORMAL,
                                        true
                                )
                        )
                );

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().isProtectedAnchor())
                .isTrue();
    }

    @Test
    void ordinaryLowDetailWithoutAnchorIsNotStored() {

        Memory memory = memory();

        List<ProtectedMemoryDetail> saved =
                service.saveDetails(
                        memory,
                        List.of(
                                new MemoryDetailCandidate(
                                        "창문이 열려 있었다",
                                        MemoryDetailSalience.LOW,
                                        false
                                )
                        )
                );

        assertThat(saved).isEmpty();

        verify(details, never())
                .save(any());
    }

    @Test
    void duplicateDetailKeepsHigherSalience() {

        Memory memory = memory();

        when(details.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ProtectedMemoryDetail> saved =
                service.saveDetails(
                        memory,
                        List.of(
                                new MemoryDetailCandidate(
                                        "붉은 반지를 건넸다",
                                        MemoryDetailSalience.NORMAL,
                                        true
                                ),
                                new MemoryDetailCandidate(
                                        "붉은 반지를 건넸다",
                                        MemoryDetailSalience.CRITICAL,
                                        false
                                )
                        )
                );

        assertThat(saved).hasSize(1);

        assertThat(saved.getFirst().getSalience())
                .isEqualTo(
                        MemoryDetailSalience.CRITICAL
                );

        verify(details, times(1))
                .save(any());
    }

    @Test
    void explicitAnchorWinsWhenSalienceIsEqual() {

        Memory memory = memory();

        when(details.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ProtectedMemoryDetail> saved =
                service.saveDetails(
                        memory,
                        List.of(
                                new MemoryDetailCandidate(
                                        "그날의 약속",
                                        MemoryDetailSalience.NORMAL,
                                        false
                                ),
                                new MemoryDetailCandidate(
                                        "그날의 약속",
                                        MemoryDetailSalience.NORMAL,
                                        true
                                )
                        )
                );

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().isProtectedAnchor())
                .isTrue();
    }

    @Test
    void invalidAndOversizedDetailsAreIgnored() {

        Memory memory = memory();

        List<ProtectedMemoryDetail> saved =
                service.saveDetails(
                        memory,
                        List.of(
                                new MemoryDetailCandidate(
                                        " ",
                                        MemoryDetailSalience.CRITICAL,
                                        true
                                ),
                                new MemoryDetailCandidate(
                                        "x".repeat(301),
                                        MemoryDetailSalience.CRITICAL,
                                        true
                                )
                        )
                );

        assertThat(saved).isEmpty();

        verify(details, never())
                .save(any());
    }

    @Test
    void 중복_detail은_높은_salience와_explicit_anchor를_각각_보존한다() {

        Memory memory =
                mock(Memory.class);

        when(memory.getId())
                .thenReturn(1L);

        ProtectedMemoryDetailRepository repository =
                mock(ProtectedMemoryDetailRepository.class);

        when(repository.save(any(ProtectedMemoryDetail.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        MemoryDetailService service =
                new MemoryDetailService(repository);

        List<ProtectedMemoryDetail> saved =
                service.saveDetails(
                        memory,
                        List.of(
                                new MemoryDetailCandidate(
                                        "붉은 반지",
                                        MemoryDetailSalience.CRITICAL,
                                        false
                                ),
                                new MemoryDetailCandidate(
                                        "붉은 반지",
                                        MemoryDetailSalience.LOW,
                                        true
                                )
                        )
                );

        assertThat(saved)
                .hasSize(1);

        ProtectedMemoryDetail detail =
                saved.getFirst();

        assertThat(detail.getDetailText())
                .isEqualTo("붉은 반지");

        assertThat(detail.getSalience())
                .isEqualTo(MemoryDetailSalience.CRITICAL);

        assertThat(detail.isProtectedAnchor())
                .isTrue();
    }

    private Memory memory() {

        Character character =
                new Character("NPC");

        Memory memory =
                new Memory(
                        character,
                        null,
                        "original",
                        5,
                        50,
                        LocalDateTime.of(
                                2026,
                                1,
                                1,
                                12,
                                0
                        )
                );

        org.springframework.test.util.ReflectionTestUtils
                .setField(
                        memory,
                        "id",
                        1L
                );

        return memory;
    }
}